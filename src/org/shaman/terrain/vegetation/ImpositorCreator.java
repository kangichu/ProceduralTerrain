/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.shaman.terrain.vegetation;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingVolume;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.system.JmeContext;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.sourceforge.arbaro.export.ExporterFactory;
import net.sourceforge.arbaro.export.Progress;
import net.sourceforge.arbaro.mesh.MeshGenerator;
import net.sourceforge.arbaro.mesh.MeshGeneratorFactory;
import net.sourceforge.arbaro.params.Params;
import net.sourceforge.arbaro.tree.Tree;
import net.sourceforge.arbaro.tree.TreeGenerator;
import net.sourceforge.arbaro.tree.TreeGeneratorFactory;
import org.apache.commons.collections4.MultiMap;
import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.shaman.terrain.ArbaroToJmeExporter;
import org.shaman.terrain.Biome;

/**
 *
 * @author Sebastian Weiss
 */
public class ImpositorCreator extends SimpleApplication{
	private static final Logger LOG = Logger.getLogger(ImpositorCreator.class.getName());
	private static final String INPUT_FOLDER = "./trees/";
	public static final String OUTPUT_FOLDER = "./treemesh/";
	public static final String TREE_DATA_FILE = OUTPUT_FOLDER + "Trees.dat";
	private static final String TREE_DEF_FILE = "./Trees.csv";
	private static final float MAX_PROP = 0.15f;
	private static final int TEXTURE_SIZE = 1024;
	private static final int OUTPUT_TEXTURE_SIZE = 512;
	public static final int IMPOSITOR_COUNT = 8;

	private final Random rand = new Random();
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		new ImpositorCreator().start(JmeContext.Type.OffscreenSurface);
	}

	@Override
	public void simpleInitApp() {
		File folder = new File(OUTPUT_FOLDER);
		if (folder.exists()) {
			assert (folder.isDirectory());
		} else {
			folder.mkdir();
		}
		
		List<TreeInfo> trees = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		//collect input
		MultiMap<Biome, Pair<String, Float>> treeDef = new MultiValueMap<>();
		Map<String, Float> probabilities = new HashMap<>();
		try (BufferedReader in = new BufferedReader(new FileReader(TREE_DEF_FILE))) {
			in.readLine(); //skip head
			while (true) {
				String line = in.readLine();
				if (line == null) break;
				String[] parts = line.split(";");
				Biome biome = Biome.valueOf(parts[0]);
				String treeName = parts[1];
				float prob = Float.parseFloat(parts[2]) / 100f;
				treeDef.put(biome, new ImmutablePair<>(treeName, prob));
				Float p = probabilities.get(treeName);
				if (p==null) {
					p = prob;
				} else {
					p += prob;
				}
				probabilities.put(treeName, p);
			}
		} catch (IOException ex) {
			Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
		}
		LOG.info("TreeDef: "+treeDef);
		LOG.info("TreeProb: "+probabilities);
		//create trees
		treeCreation:
		for (Map.Entry<String, Float> entry  : probabilities.entrySet()) {
			try {
				String treeName = entry.getKey();
				List<TreeInfo> treeInfos = new ArrayList<>();
				float prob = entry.getValue();
				if (prob <= MAX_PROP) {
					TreeInfo info = createTree(null, treeName, 0, 1);
					if (info != null) {
						treeInfos.add(info);
					} else {
						continue treeCreation;
					}
				} else {
					int n = (int) Math.ceil(prob / MAX_PROP);
					float p = prob / n;
					for (int i = 0; i < n; ++i) {
						TreeInfo info = createTree(null, treeName, i, p);
						if (info != null) {
							treeInfos.add(info);
						} else {
							continue treeCreation;
						}
					}
				}
				//create tree infos
				for (Map.Entry<Biome, Object> treeDefE : treeDef.entrySet()) {
					for (Pair<String, Float> v : (Collection<Pair<String, Float>>) treeDefE.getValue()) {
						if (treeName.equals(v.getKey())) {
							for (TreeInfo i : treeInfos) {
								TreeInfo i2 = i.clone();
								i2.biome = treeDefE.getKey();
								i2.probability = v.getValue() / treeInfos.size();
								trees.add(i2);
							}
						}
					}
				}
			} catch (IOException ex) {
				Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		System.out.println("trees:");
		for (TreeInfo t : trees) {
			System.out.println(" "+t);
		}
		LOG.log(Level.INFO, "save tree infos, {0} trees in total", trees.size());
		try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(TREE_DATA_FILE)))) {
			out.writeObject(trees);
		} catch (IOException ex) {
			Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
		}
		LOG.info("done");
		
		stop();
	}

	public TreeInfo createTree(Biome biome, String treeName, int variation, float prob) throws IOException {
		LOG.info("create tree from "+treeName);
		
		File folder = new File(OUTPUT_FOLDER + treeName + "_v" + variation);
		if (!folder.exists() && !folder.mkdir()) {
			LOG.log(Level.SEVERE, "unable to make directory {0}", folder);
			return null;
		}
		
		//create tree
		Params params = new Params();
		params.prepare(13);
		params.clearParams();
		params.readFromXML(new FileInputStream(INPUT_FOLDER + treeName + ".xml"));
		params.prepare(rand.nextInt(Short.MAX_VALUE)); 
		TreeGenerator treeGenerator = TreeGeneratorFactory.createTreeGenerator(params);
		treeGenerator.setSeed(rand.nextInt(Short.MAX_VALUE));
		treeGenerator.setParam("Smooth",params.getParam("Smooth").toString());
		ExporterFactory.setRenderW(1024);
		ExporterFactory.setRenderH(1024);
		ExporterFactory.setExportFormat(-1);
		ExporterFactory.setOutputStemUVs(true);
		ExporterFactory.setOutputLeafUVs(true);
		Progress progress = new Progress();
		Tree treeData = treeGenerator.makeTree(progress);
		MeshGenerator meshGenerator = MeshGeneratorFactory.createMeshGenerator(/*params,*/ true);
		ArbaroToJmeExporter exporter = new ArbaroToJmeExporter(assetManager, treeData, meshGenerator);
		exporter.setBarkTexture("org/shaman/terrain/textures2/"+params.getParam("BarkTexture").getValue());
		exporter.setLeafTexture("org/shaman/terrain/textures2/"+params.getParam("LeafTexture").getValue());
		exporter.setLeafRotation(Float.parseFloat(params.getParam("LeafTextureRotation").getValue()));
		exporter.doWrite();
		Spatial tree = exporter.getSpatial();
		LOG.log(Level.INFO, "tree generated, vertices:{0}, triangles:{1}", 
				new Object[]{tree.getVertexCount(), tree.getTriangleCount()});
		
		//save tree
		BinaryExporter binaryExporter = new BinaryExporter();
		binaryExporter.save(tree, new File(folder, "Tree.j3o"));
		
		//compute bounding cylinder
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
		Vector3f lightDir = new Vector3f(-0.1f, -0.1f, -0.1f);
		light.setDirection(lightDir.normalize());
		AmbientLight ambientLight = new AmbientLight(new ColorRGBA(0.6f, 0.6f, 0.6f, 1));
		sceneNode.addLight(ambientLight);
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
		sceneNode.addLight(light);
		for (int i=0; i<IMPOSITOR_COUNT; ++i) {
			sceneNode3.updateGeometricState();
			
			renderManager.renderViewPort(view, 0);
			renderer.readFrameBuffer(buffer, data);
			sceneNode.rotate(0, 0, FastMath.TWO_PI / IMPOSITOR_COUNT);
			Quaternion rot = new Quaternion();
			rot.fromAngles(0, 0, i*FastMath.TWO_PI / IMPOSITOR_COUNT);
			light.setDirection(rot.mult(lightDir).normalizeLocal());
			
			try {
				convertScreenShot(data, image);
				BufferedImage img = new BufferedImage(OUTPUT_TEXTURE_SIZE, OUTPUT_TEXTURE_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D G = img.createGraphics();
				G.drawImage(image, 0, 0, OUTPUT_TEXTURE_SIZE, OUTPUT_TEXTURE_SIZE, null);
				G.dispose();
				ImageIO.write(img, "png", new File(folder, i+".png"));
			} catch (IOException ex) {
				Logger.getLogger(ImpositorCreator.class.getName()).log(Level.SEVERE, null, ex);
				return null;
			}
		}
		view.clearScenes();

		//create tree info
		TreeInfo info = new TreeInfo();
		info.biome = biome;
		info.name = treeName + "_v" + variation;
		info.treeSize = height;
		info.probability = prob;
		info.impostorCount = IMPOSITOR_COUNT;
		info.impostorFadeNear = 30;
		info.impostorFadeFar = 50;
		info.highResStemFadeNear = 30;
		info.highResStemFadeFar = 50;
		info.highResLeavesFadeNear = 35;
		info.highResLeavesFadeFar = 55;
		
		System.out.println("impostors created");
		Runtime.getRuntime().gc();
		assetManager.clearCache();
		
		return info;
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
