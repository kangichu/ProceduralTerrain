/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.shaman.terrain.vegetation;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.bounding.BoundingVolume;
import com.jme3.export.binary.BinaryImporter;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.system.JmeContext;
import com.jme3.system.JmeSystem;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author Sebastian Weiss
 */
public class ImpositorCreator extends SimpleApplication{
	private static final String TREE_FILE = "./treemesh/Tree1.j3o";
	private static final String OUTPUT_FOLDER = "./treemesh/Tree1/";
	private static final int TEXTURE_SIZE = 1024;
	public static final int IMPOSITOR_COUNT = 8;

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		new ImpositorCreator().start(JmeContext.Type.OffscreenSurface);
	}

	@Override
	public void simpleInitApp() {
		//load tree
		BinaryImporter importer = new BinaryImporter();
		importer.setAssetManager(assetManager);
		Spatial tree = null;
		try {
			tree = (Spatial) importer.load(new File(TREE_FILE));
		} catch (IOException ex) {
			Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
			stop();
			return;
		}
		List<Geometry> geometries = new ArrayList<>();
		listGeometries(tree, geometries);
		System.out.println("count of geometries: "+geometries.size());
		//compute bounding cylinder, assuming that the tree starts at the origin and grows in z-direction
		BoundingVolume oldBoundingVolume = tree.getWorldBound();
		System.out.println("original bounding volume: "+oldBoundingVolume);
		float radius = 0;
		float height = 0;
		Vector3f pos = new Vector3f();
		Vector3f pos2 = new Vector3f();
		for (Geometry geom : geometries) {
			Mesh mesh = geom.getMesh();
			Transform trafo = geom.getWorldTransform();
			VertexBuffer buffer = mesh.getBuffer(VertexBuffer.Type.Position);
			FloatBuffer fbuf = (FloatBuffer) buffer.getData();
			fbuf.rewind();
			for (int i=0; i<buffer.getNumElements(); ++i) {
				pos.x = fbuf.get();
				pos.y = fbuf.get();
				pos.z = fbuf.get();
				pos2 = trafo.transformVector(pos, pos2);
				radius = Math.max(radius, pos2.x*pos2.x + pos2.y*pos2.y);
				height = Math.max(height, pos2.z);
			}
			fbuf.rewind();
		}
		radius = FastMath.sqrt(radius);
		System.out.println("cylinder radius: "+radius+", height: "+height);
		
		//setup scene
		Node sceneNode = new Node("scene");
		sceneNode.attachChild(tree);
		DirectionalLight light = new DirectionalLight();
        light.setDirection((new Vector3f(-0.1f, -0.1f, -0.1f)).normalize());
        tree.addLight(light);
		AmbientLight ambientLight = new AmbientLight(new ColorRGBA(0.6f, 0.6f, 0.6f, 1));
//		sceneNode.addLight(ambientLight);
		sceneNode.setQueueBucket(RenderQueue.Bucket.Gui);
		for (Geometry geom : geometries) {
			geom.setQueueBucket(RenderQueue.Bucket.Gui);
			geom.getMaterial().setFloat("FadeNear", 2000);
			geom.getMaterial().setFloat("FadeFar", 3000);
		}
		//transform to match texture size
		Node sceneNode2 = new Node("scene2");
		sceneNode2.attachChild(sceneNode);
		sceneNode2.rotate(-FastMath.HALF_PI, 0, 0);
		float scale = TEXTURE_SIZE / Math.max(height,2*radius);
		sceneNode2.scale(scale);
		Node sceneNode3 = new Node("scene3");
		sceneNode3.attachChild(sceneNode2);
		sceneNode3.setLocalTranslation(TEXTURE_SIZE/2, 0, 0);
		
		//create offscreen surface
		ByteBuffer data = BufferUtils.createByteBuffer(TEXTURE_SIZE * TEXTURE_SIZE * 4);
		BufferedImage image = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
		Camera camera = new Camera(TEXTURE_SIZE, TEXTURE_SIZE);
		camera.setParallelProjection(true);
		final ViewPort view = new ViewPort("Off", camera);
		view.setBackgroundColor(ColorRGBA.BlackNoAlpha);
		view.setClearFlags(true, true, true);
		final FrameBuffer buffer = new FrameBuffer(TEXTURE_SIZE, TEXTURE_SIZE, 1);
		buffer.setDepthBuffer(Image.Format.Depth);
		buffer.setColorBuffer(Image.Format.RGBA32F);
		view.setOutputFrameBuffer(buffer);
		view.attachScene(sceneNode3);
		sceneNode3.setCullHint(Spatial.CullHint.Never);
		sceneNode2.setCullHint(Spatial.CullHint.Never);
		sceneNode.setCullHint(Spatial.CullHint.Never);
		tree.setCullHint(Spatial.CullHint.Never);
		view.setEnabled(true);
		//render
		for (int i=0; i<IMPOSITOR_COUNT; ++i) {
			sceneNode3.updateGeometricState();
			renderManager.renderViewPort(view, 0);
			renderer.readFrameBuffer(buffer, data);
			sceneNode.rotate(0, 0, FastMath.TWO_PI / IMPOSITOR_COUNT);
			
//			try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(OUTPUT_FOLDER+i+".png"))) {
//				JmeSystem.writeImageFile(out, "png", data, TEXTURE_SIZE, TEXTURE_SIZE);
//			} catch (IOException ex) {
//				Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
//			}
			
			try {
				convertScreenShot(data, image);
				ImageIO.write(image, "png", new File(OUTPUT_FOLDER+i+".png"));
			} catch (IOException ex) {
				Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		System.out.println("impositors created");
		
		stop();
	}
	private void listGeometries(Spatial s, List<Geometry> geometries) {
		if (s instanceof Geometry) {
			geometries.add((Geometry) s);
		} else if (s instanceof Node) {
			for (Spatial c : ((Node) s).getChildren()) {
				listGeometries(c, geometries);
			}
		}
	}
	
	public static void convertScreenShot(ByteBuffer bgraBuf, BufferedImage out){
        WritableRaster wr = out.getRaster();
        DataBufferByte db = (DataBufferByte) wr.getDataBuffer();

        byte[] cpuArray = db.getData();

        // copy native memory to java memory
        bgraBuf.clear();
        bgraBuf.get(cpuArray);
        bgraBuf.clear();

        int width  = wr.getWidth();
        int height = wr.getHeight();

        // flip the components the way AWT likes them
        
        // calcuate half of height such that all rows of the array are written to
        // e.g. for odd heights, write 1 more scanline
        int heightdiv2ceil = height % 2 == 1 ? (height / 2) + 1 : height / 2;
        for (int y = 0; y < heightdiv2ceil; y++){
            for (int x = 0; x < width; x++){
                int inPtr  = (y * width + x) * 4;
                int outPtr = ((height-y-1) * width + x) * 4;

                byte b1 = cpuArray[inPtr+0];
                byte g1 = cpuArray[inPtr+1];
                byte r1 = cpuArray[inPtr+2];
                byte a1 = cpuArray[inPtr+3];

                byte b2 = cpuArray[outPtr+0];
                byte g2 = cpuArray[outPtr+1];
                byte r2 = cpuArray[outPtr+2];
                byte a2 = cpuArray[outPtr+3];

                cpuArray[outPtr+0] = a1;
                cpuArray[outPtr+1] = r1;//b1;
                cpuArray[outPtr+2] = g1;
                cpuArray[outPtr+3] = b1;//r1;

                cpuArray[inPtr+0] = a2;
                cpuArray[inPtr+1] = r2;//b2;
                cpuArray[inPtr+2] = g2;
                cpuArray[inPtr+3] = b2;//r2;
            }
        }
    }
}