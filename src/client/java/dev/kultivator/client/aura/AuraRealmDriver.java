package dev.kultivator.client.aura;

/**
 * The aura of one realm (DESIGN 31.8 C31.1), registered with {@link AuraEngine#register}. Drivers are stateless: all
 * per-entity state lives in the {@link AuraRig} (eased values, props, scratch data). Shapes are functions of
 * {@link AuraRig#time()} and the eased stage values, never of a random source; "breathing" is alpha or prop light only.
 * The engine adds the shared parts itself: the accent ring and sparks, the breakthrough-ready glow and crack lines, the
 * ring of light rolled out on a stage change, the first-person view, the hum and every budget.
 */
public interface AuraRealmDriver {

	/** The aura of this realm begins (props may grow in here; ask for them again from {@link #tick} to keep them coming back). */
	void start(AuraSubject s, AuraRig rig);

	/** Every unpaused client tick while the fade is above 0 (particles, props). Not called for your own first-person view. */
	void tick(AuraSubject s, AuraRig rig);

	/** Every frame: quads only, through {@code draw} (every draw call is already × the subject's fade). */
	void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial);

	/** The fade-out is done or the realm changed: end every prop. */
	void stop(AuraSubject s, AuraRig rig);

	/** The hum and chime of 31.4 ({@link AuraHum.Spec#NONE} for none). */
	AuraHum.Spec hum(AuraSubject s);

	/**
	 * The realm's widest radius in blocks at scale 1 (the accent ring sits at 1.15 × this, the amber glow and the stage
	 * ring reach about this far). Optional.
	 */
	default float reach(AuraSubject s) {
		return 1.6F;
	}
}
