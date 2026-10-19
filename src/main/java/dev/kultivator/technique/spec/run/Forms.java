package dev.kultivator.technique.spec.run;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TRANSFORM (DESIGN 22.3.3): one form per caster at a time; a SCALE above 1 only as large as the Dharma head-room check
 * allows (at most 4), a transient {@code kultivator:form_scale} modifier, the form's own boons for its duration, and on
 * revert a safe spot when the body would end inside a wall. A low ceiling ends a grown form early. Legacy Dharma Form
 * shares the slot ({@code DharmaForm} ends any technique form and is ended by one). Every client learns the form from
 * {@code Payloads.FormSync} (start, stage, end and every 2 s; DESIGN 27.4) and draws its pieces over the body.
 */
public final class Forms {

	public static final double MAX_SCALE = 4.0D;
	private static final Identifier SCALE_ID = Kultivator.id("form_scale");

	private static final class Form {
		final Caster source;
		final String id;
		final double scale;
		final long until;
		int stage;

		Form(Caster source, String id, double scale, long until) {
			this.source = source;
			this.id = id;
			this.scale = scale;
			this.until = until;
		}
	}

	/**
	 * The client form id of a TRANSFORM form (DESIGN 27.4): the 72 transformations as {@code seventy_two:<shape>},
	 * fa_tian_xiang_di as the {@code dharma} armour, the rest as their own id.
	 */
	public static String visualId(String form) {
		return switch (form) {
			case "seventy_two_harimau" -> "seventy_two:tiger";
			case "seventy_two_elang" -> "seventy_two:eagle";
			case "seventy_two_ikan" -> "seventy_two:fish";
			case "seventy_two_serangga" -> "seventy_two:insect";
			case "seventy_two_kuil" -> "seventy_two:temple";
			case "fa_tian_xiang_di" -> "dharma";
			case "lei_zhenzi_wings" -> "lei_zhenzi";
			default -> form;
		};
	}

	private static void sync(LivingEntity who, Form form) {
		VisualSync.form(who, visualId(form.id), (int) Math.max(0L, form.until - who.level().getGameTime()), form.stage);
	}

	/** A form-specific stage changed (ancient god stomps, heaven fire 1-3): resent to the clients. */
	public static void setStage(LivingEntity who, int stage) {
		Form form = FORMS.get(who.getUUID());
		if (form != null && form.stage != stage) {
			form.stage = stage;
			sync(who, form);
		}
	}

	/** The active form's stage (0 for none). */
	public static int stage(LivingEntity who) {
		Form form = FORMS.get(who.getUUID());
		return form == null ? 0 : form.stage;
	}

	private static final Map<UUID, Form> FORMS = new HashMap<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FORMS.clear());
	}

	/**
	 * Starts a form: the previous one (or a Dharma Form) ends first; the scale shrinks until the body fits (never below
	 * 1). The form's boons are applied by the caller. True when the form started.
	 */
	static boolean start(Caster source, LivingEntity who, Payload.Transform transform, int ticks, double requestedScale) {
		end(who, EndReason.REPLACED);
		if (who instanceof ServerPlayer player && Cultivators.get(player).dharmaActive()) {
			dev.kultivator.technique.Techniques.endDharma(player);
		}
		double scale = Math.max(0.1D, Math.min(MAX_SCALE, requestedScale));
		if (scale > 1.0D) {
			double fits = 1.0D;
			for (double candidate = scale; candidate > 1.0D; candidate -= 0.5D) {
				if (room(who, candidate)) {
					fits = candidate;
					break;
				}
			}
			scale = fits;
		}
		if (Math.abs(scale - 1.0D) > 1.0E-3D) {
			AttributeInstance instance = who.getAttribute(Attributes.SCALE);
			if (instance != null) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ID, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		Form made = new Form(source, transform.form(), scale, who.level().getGameTime() + Math.max(1, ticks));
		if (transform.form().startsWith("heaven_fire_")) {
			made.stage = Math.max(1, Math.min(3, transform.form().charAt(transform.form().length() - 1) - '0'));
		}
		FORMS.put(who.getUUID(), made);
		sync(who, made);
		return true;
	}

	/** The active form's id, or null. */
	public static @Nullable String form(LivingEntity who) {
		Form form = FORMS.get(who.getUUID());
		return form == null ? null : form.id;
	}

	/** The active form's scale (1 for none). */
	public static double scale(LivingEntity who) {
		Form form = FORMS.get(who.getUUID());
		return form == null ? 1.0D : form.scale;
	}

	/** Game time the active form ends (0 for none). */
	public static long until(LivingEntity who) {
		Form form = FORMS.get(who.getUUID());
		return form == null ? 0L : form.until;
	}

	/** Ends the form of {@code who} (if any): scale back, the form's boons removed, a safe spot when stuck in a wall. */
	static void end(LivingEntity who, EndReason reason) {
		Form form = FORMS.remove(who.getUUID());
		if (form == null) {
			return;
		}
		VisualSync.form(who, "", 0, 0);
		if (reason != EndReason.DONE && reason != EndReason.REPLACED) {
			// the form's lasting layers end on every client too (S8)
			form.source.abortFx();
		}
		AttributeInstance instance = who.getAttribute(Attributes.SCALE);
		if (instance != null) {
			instance.removeModifier(SCALE_ID);
		}
		Boons.endCast(form.source);
		form.source.formEnded(reason);
		if (who.level() instanceof ServerLevel level && !level.noBlockCollision(who, who.getBoundingBox().deflate(0.02D))) {
			Vec3 spot = Targeting.safeSpot(level, who, who.position());
			if (spot != null) {
				if (who instanceof ServerPlayer player) {
					player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
				} else {
					who.teleportTo(spot.x, spot.y, spot.z);
				}
			}
		}
	}

	/** Forms of casts that ended (a revert payload, death, logout): see {@link #end}. */
	static void endFrom(Caster source, EndReason reason) {
		Form form = FORMS.get(source.caster().getUUID());
		if (form != null && form.source == source) {
			end(source.caster(), reason);
		}
	}

	/** Every tick: expiry and the ceiling check of grown forms (every second). */
	static void tick(long serverTick) {
		if (FORMS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Form> entry : Map.copyOf(FORMS).entrySet()) {
			Form form = entry.getValue();
			LivingEntity who = form.source.caster();
			if (!form.source.casterValid()) {
				FORMS.remove(entry.getKey());
				continue;
			}
			long now = who.level().getGameTime();
			if (now >= form.until) {
				end(who, EndReason.DONE);
			} else if (serverTick % 40 == 7) {
				// late arrivals and missed packets: the form is resent every 2 s
				sync(who, form);
			} else if (form.scale > 1.0D && serverTick % 20 == 0 && who.level() instanceof ServerLevel level
					&& !level.noBlockCollision(who, who.getBoundingBox().deflate(0.02D))) {
				end(who, EndReason.CANCELLED);
			}
		}
	}

	/** A body {@code scale} times the standing size fits here (read-only). */
	private static boolean room(LivingEntity who, double scale) {
		if (!(who.level() instanceof ServerLevel level)) {
			return false;
		}
		double current = who instanceof ServerPlayer ? Math.max(1.0E-3D, who.getScale()) : 1.0D;
		AABB box = who.getDimensions(Pose.STANDING).scale((float) (scale / current)).makeBoundingBox(who.position());
		return level.noBlockCollision(who, box);
	}

	/** True while a grown technique form is active (suffocation is cancelled as for Dharma Form). */
	public static boolean grown(LivingEntity who) {
		return scale(who) > 1.0D;
	}

	static int count() {
		return FORMS.size();
	}

	private Forms() {
	}
}
