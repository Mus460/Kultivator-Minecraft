package dev.kultivator.client.dunia.kota42.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

import java.util.function.Function;

/**
 * The cuboid model of every 42V-M renderer (DESIGN 42.12.4): a mesh of {@code model/KotaMesh} (generated with its atlas by
 * {@code tools/gen_kota_hidup_assets.py}) whose parts are looked up by name, posed each frame by one {@link Gerak} function
 * of the render state. {@code Model.setupAnim} resets every part's pose first; visibility is not reset, so a pose sets
 * {@code visible} on every part it toggles. Client only.
 */
public class KotaModel<S extends EntityRenderState> extends EntityModel<S> {

	/** Poses a model from its state (called after the pose reset). */
	@FunctionalInterface
	public interface Gerak<S extends EntityRenderState> {
		void pose(KotaModel<S> model, S state);
	}

	private final Function<String, ModelPart> lookup;
	private final Gerak<S> gerak;

	public KotaModel(ModelPart root, Gerak<S> gerak) {
		super(root);
		this.lookup = root.createPartLookup();
		this.gerak = gerak;
	}

	/** The part named {@code name} (a programming error if the mesh has none). */
	public ModelPart p(String name) {
		ModelPart part = lookup.apply(name);
		if (part == null) {
			throw new IllegalArgumentException("KotaModel: no part " + name);
		}
		return part;
	}

	/** True when the mesh has a part named {@code name}. */
	public boolean ada(String name) {
		return lookup.apply(name) != null;
	}

	/** Shows exactly one of {@code prefix + 0 .. prefix + (n - 1)}: {@code pilih} (others hidden; out of range = none). */
	public void satu(String prefix, int n, int pilih) {
		for (int i = 0; i < n; i++) {
			p(prefix + i).visible = i == pilih;
		}
	}

	@Override
	public void setupAnim(S state) {
		super.setupAnim(state);
		gerak.pose(this, state);
	}

	// ------------------------------------------------------------------ small motion helpers

	/** A pendulum sway of amplitude {@code amp} radians (wind on a hanging lantern), phase-shifted per part. */
	public static float ayun(float waktu, float fase, float amp, float kecepatan) {
		return Mth.sin(waktu * kecepatan + fase) * amp + Mth.sin(waktu * kecepatan * 2.3F + fase * 1.7F) * amp * 0.3F;
	}

	/** Clamped progress of {@code t} over {@code [a, a + len]}. */
	public static float maju(float t, float a, float len) {
		return Mth.clamp((t - a) / len, 0.0F, 1.0F);
	}
}
