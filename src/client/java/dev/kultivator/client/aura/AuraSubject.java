package dev.kultivator.client.aura;

import dev.kultivator.data.Realm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable snapshot of one cultivator whose Aura Kultivasi is shown (DESIGN 31.8 C31.2). The engine makes one per
 * client tick (for {@code tick}) and one per frame with the position, body yaw and fade lerped by the partial tick (for
 * {@code render}).
 *
 * @param entityId        the cultivator's entity id
 * @param entity          the entity (null once it is gone; the aura then fades where it was last seen)
 * @param disciple        a disciple (else a player)
 * @param own             yourself, or one of your own disciples
 * @param feet            the feet position (lerped in {@code render})
 * @param scale           the entity scale; every radius of 31.4 is multiplied by it
 * @param height          the visible height: the bounding box height, × 0.69 while seated
 * @param bodyYaw         the body yaw (degrees, Minecraft convention: 0 faces +z)
 * @param seated          sitting cross-legged (the pose lowers the model)
 * @param onGround        on the ground (ground decals are drawn only then)
 * @param realm           the realm shown (the preview's realm during a preview)
 * @param layer           the layer 0..8 (Immortal Ascension only, else 0)
 * @param stage           0 Awal, 1 Tengah, 2 Akhir, 3 Puncak
 * @param dao             the Dao ordinal, −1 for none
 * @param rootColor       the root's main colour
 * @param rootElements    the root element mask (0 untested)
 * @param accent          the accent colour (Heavenly root, a disciple's physique, a forced preview accent), −1 for none
 * @param bottleneck      ready to break through (the amber pulse)
 * @param ascended        an ascended True Immortal
 * @param dharma          the subject's own Dharma Idol is up
 * @param fade            0..1, the eased lifecycle fade (× 0.5 while breaking through)
 * @param lod             1 full, 0.5 reduced
 * @param share           1 for yourself and your disciples, the "Efek pemain lain" factor for others
 * @param firstPersonSelf your own aura seen through your own eyes (no world geometry)
 * @param simple          "Aura Kultivasi: Sederhana"
 * @param preview         shown by a {@code /kultivasi uji aura} preview
 */
public record AuraSubject(int entityId, @Nullable Entity entity, boolean disciple, boolean own, Vec3 feet, double scale, double height,
		float bodyYaw, boolean seated, boolean onGround, Realm realm, int layer, int stage, int dao, int rootColor, int rootElements,
		int accent, boolean bottleneck, boolean ascended, boolean dharma, float fade, float lod, float share, boolean firstPersonSelf,
		boolean simple, boolean preview) {

	/** The lower dantian: 0.45 b above the feet while seated, 0.9 standing (× scale). */
	public Vec3 dantian() {
		return feet.add(0.0D, (seated ? 0.45D : 0.9D) * scale, 0.0D);
	}

	/** Just above the head. */
	public Vec3 crown() {
		return feet.add(0.0D, height + 0.15D * scale, 0.0D);
	}

	/** The top of the seat (the lap) of a seated body, the hips of a standing one. */
	public Vec3 seatTop() {
		return feet.add(0.0D, (seated ? 0.55D : 0.75D) * scale, 0.0D);
	}

	/** The middle of the visible body. */
	public Vec3 chest() {
		return feet.add(0.0D, height * 0.62D, 0.0D);
	}

	/** {@code blocks} at scale 1 in blocks at this subject's scale. */
	public float r(double blocks) {
		return (float) (blocks * scale);
	}

	/** The way the body faces (unit, horizontal). */
	public Vec3 forward() {
		double yaw = Math.toRadians(bodyYaw);
		return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
	}

	/** The body's right-hand side (unit, horizontal). */
	public Vec3 right() {
		double yaw = Math.toRadians(bodyYaw);
		return new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
	}

	/** A point around the body: {@code angleDeg} from the front (positive toward the right), {@code radius} out, {@code up} above the feet. */
	public Vec3 around(double angleDeg, double radius, double up) {
		double a = Math.toRadians(angleDeg);
		Vec3 f = forward();
		Vec3 rt = right();
		double c = Math.cos(a) * radius;
		double s = Math.sin(a) * radius;
		return feet.add(f.x * c + rt.x * s, up, f.z * c + rt.z * s);
	}

	/** Grows with the stage: 0.55, 0.7, 0.85, 1.0. */
	public float stageGrow() {
		return 0.55F + 0.15F * Math.max(0, Math.min(3, stage));
	}

	/** Grows with the layers of Immortal Ascension (layer / 8), 0 in every other realm. */
	public float layerGrow() {
		return realm == Realm.IMMORTAL_ASCENSION ? Math.max(0, Math.min(8, layer)) / 8.0F : 0.0F;
	}

	public boolean reduced() {
		return lod < 1.0F;
	}

	/** True when only the essentials are drawn (reduced LOD or "Sederhana"). */
	public boolean essentials() {
		return lod < 1.0F || simple;
	}

	public AuraColors.Palette colors() {
		return AuraColors.of(this);
	}

	/** The same subject with the engine's decisions of this tick (fade, LOD, share, view, Sederhana). */
	public AuraSubject withState(float newFade, float newLod, float newShare, boolean newFirstPersonSelf, boolean newSimple) {
		return new AuraSubject(entityId, entity, disciple, own, feet, scale, height, bodyYaw, seated, onGround, realm, layer, stage, dao,
				rootColor, rootElements, accent, bottleneck, ascended, dharma, newFade, newLod, newShare, newFirstPersonSelf, newSimple, preview);
	}

	/** The same subject at a frame: lerped feet and body yaw, the fade of that frame. */
	public AuraSubject atFrame(Vec3 framedFeet, float framedYaw, float framedFade) {
		return new AuraSubject(entityId, entity, disciple, own, framedFeet, scale, height, framedYaw, seated, onGround, realm, layer, stage, dao,
				rootColor, rootElements, accent, bottleneck, ascended, dharma, framedFade, lod, share, firstPersonSelf, simple, preview);
	}
}
