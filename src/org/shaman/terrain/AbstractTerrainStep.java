/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.shaman.terrain;

import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.shaman.terrain.erosion.RiverSource;

/**
 *
 * @author Sebastian Weiss
 */
public abstract class AbstractTerrainStep implements AppState {
	
	/**
	 * Key for the property holding the heigtmap of type {@link Heightmap}.
	 * Zero height is the ocean level, positive is above, negative is below the sea
	 */
	public static final String KEY_HEIGHTMAP = "heightmap";
	/**
	 * Key for the property storing the temperature in a {@link Heightmap}.
	 * A zero value means cold, one means hot.
	 */
	public static final String KEY_TEMPERATURE = "temperature";	
	/**
	 * Key for the property storing the moisture in a {@link Heightmap}.
	 * A zero value means dry, one means wet.
	 */
	public static final String KEY_MOISTURE = "moisture";
	/**
	 * Key for a {@link Vectorfield} describing the influence of each biome at
	 * each position on the terrain. The dimension of the vectorfield is the
	 * number of constants in {@link Biome}, the entries are indexed by the
	 * Biome's oridinal values.
	 */
	public static final String KEY_BIOMES = "biomes";
	/**
	 * Key for the property storing the water height after the water erosion
	 * in a {@link Heightmap}
	 */
	public static final String KEY_WATER = "water";
	/**
	 * Key for a list of {@link RiverSource}. These river sources are added
	 * and simulated in the hydraulic erosion step and are available in the
	 * next steps (vegetation).
	 */
	public static final String KEY_RIVER_SOURCES = "river-sources";
	/**
	 * Key for a float storing an extra scale factor of the terrain in xz-direction.
	 */
	public static final String KEY_TERRAIN_SCALE = "terrain-scale";
	
	protected AppStateManager stateManager;
	protected TerrainHeighmapCreator app;
	protected Node sceneNode;
	protected Node guiNode;
	private boolean initialized = false;
	private boolean enabled = false;
	
	protected Map<Object, Object> properties;
	
	@Override
	public final void initialize(AppStateManager stateManager, Application app) {
		this.stateManager = stateManager;
		this.app = (TerrainHeighmapCreator) app;
		this.sceneNode = new Node(getClass().getSimpleName()+"3D");
		this.guiNode = new Node(getClass().getSimpleName()+"Gui");
		initialized = true;
		if (enabled) {
			//force initialization
			enabled = false;
			setEnabled(true);
		}
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public void setEnabled(boolean active) {
		if (!initialized) {
			this.enabled = true;
			return;
		}
		if (active && !enabled) {
			enabled = true;
			app.getRootNode().attachChild(sceneNode);
			app.getGuiNode().attachChild(guiNode);
			enable();
		} else if (!active && enabled) {
			enabled = false;
			app.getRootNode().detachChild(sceneNode);
			app.getGuiNode().detachChild(guiNode);
			disable();
		}
	}
	
	protected abstract void enable();
	protected abstract void disable();
	
	protected final boolean nextStep(Class<? extends AbstractTerrainStep> nextStepClass, Map<Object, Object> properties) {
		final AbstractTerrainStep next = stateManager.getState(nextStepClass);
		if (next==null) {
			return false;
		} else {
			//auto-save stage
			Calendar cal = Calendar.getInstance();
			String name = "Auto "+this.getClass().getSimpleName()+" "
					+cal.get(Calendar.DAY_OF_YEAR)+"_"+cal.get(Calendar.HOUR_OF_DAY)
					+"_"+cal.get(Calendar.MINUTE)+"_"+cal.get(Calendar.SECOND)+".save";
			app.save(properties, nextStepClass, name);
			
			//switch to next step
			next.properties = properties;
			setEnabled(false);
			app.enqueue(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					next.setEnabled(true);
					return null;
				}
			});
		}
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void stateAttached(AppStateManager stateManager) {}

	@Override
	public void stateDetached(AppStateManager stateManager) {}

	@Override
	public void render(RenderManager rm) {}

	@Override
	public void postRender() {	}

	@Override
	public void cleanup() {}
	
}
