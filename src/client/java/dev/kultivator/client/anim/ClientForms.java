package dev.kultivator.client.anim;

import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The transformation forms of players and disciples (DESIGN 27.4, polish plan C2/C3): the synced {@code FormSync} form
 * (golden ape, three heads six arms, dragonblood, ancient god, dharma, heaven fire 1-3, thunder venerable, the
 * seventy-two bodies...) and the short visual "effects" a buff cast wears on the body, started here from the CAST event
 * of the spec with its boon duration (golden body, lightning armour, burning heart, flame or thunder wings, ice claws, the
 * azure sword glow). {@code FormLayer} draws both; {@code PoseApplier} hides the skin of forms that replace the body.
 * Times are client ticks ({@link AnimClient#ticks()}).
 */
public final class ClientForms {

	/** A synced form: {@code id} ("" none), {@code stage}, when it started (client tick) and its length in ticks. */
	public record Form(String id, int stage, long startTick, int ticks) {

		public int remaining() {
			return (int) Math.max(0L, startTick + ticks - AnimClient.ticks());
		}
	}

	/** A timed visual effect worn on the body (the overlay and back-layer looks of buffs). */
	public record Effect(String id, long startTick, int ticks) {
	}

	/** What the render state carries: the form id and stage, the ticks since it began and the worn effects. */
	public record View(String id, int stage, float age, List<String> effects) {

		public boolean has(String effect) {
			return effects.contains(effect);
		}
	}

	private static final Map<Integer, Form> FORMS = new HashMap<>();
	/** Per entity: short one-shot beats of a form (the dragon tail sweep), keyed by name, as the client tick they began. */
	private static final Map<Integer, Map<String, Long>> PULSES = new HashMap<>();
	private static final Map<Integer, List<Effect>> EFFECTS = new HashMap<>();

	/** The synced form of an entity, or null. */
	public static @Nullable Form of(int entityId) {
		Form form = FORMS.get(entityId);
		if (form != null && form.ticks() > 0 && AnimClient.ticks() > form.startTick() + form.ticks() + 20L) {
			FORMS.remove(entityId);
			return null;
		}
		return form;
	}

	/** The worn effects of an entity (empty when none). */
	public static List<Effect> effects(int entityId) {
		List<Effect> list = EFFECTS.get(entityId);
		return list == null ? List.of() : list;
	}

	/** FormSync receiver (AnimClient). */
	static void onSync(Payloads.FormSync payload) {
		if (payload.ended()) {
			FORMS.remove(payload.entity());
			return;
		}
		Form old = FORMS.get(payload.entity());
		long start = old != null && old.id().equals(payload.form()) ? old.startTick() : AnimClient.ticks();
		int ticks = Math.max(0, payload.ticks());
		// keep the original start so the age keeps counting; the length is re-based on the remaining ticks
		int length = (int) (AnimClient.ticks() - start) + ticks;
		FORMS.put(payload.entity(), new Form(payload.form(), payload.stage(), start, ticks <= 0 ? 0 : length));
	}

	/** Adds or refreshes a worn effect. */
	public static void wear(int entityId, String effect, int ticks) {
		if (ticks <= 0) {
			return;
		}
		List<Effect> list = EFFECTS.computeIfAbsent(entityId, id -> new ArrayList<>(2));
		list.removeIf(e -> e.id().equals(effect));
		list.add(new Effect(effect, AnimClient.ticks(), Math.min(ticks, 20 * 60 * 10)));
	}

	public static void takeOff(int entityId, String effect) {
		List<Effect> list = EFFECTS.get(entityId);
		if (list != null) {
			list.removeIf(e -> e.id().equals(effect));
		}
	}

	/**
	 * Buff looks started by a cast (polish plan rows 57, 59, 56, 84, 98, 114, 121, 162, 69): the CAST event carries the
	 * boon duration ({@code duration}); ABORT and END of a toggle take them off.
	 */
	static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {
		if (caster == null) {
			return;
		}
		int id = caster.getId();
		if ("dragonblood_warrior".equals(spec.id()) && phase.ordinal() >= Phase.EXTRA0.ordinal() && phase.ordinal() <= Phase.EXTRA7.ordinal()) {
			pulse(id, "tail");
		}
		String[] looks = LOOKS.get(spec.id());
		if (looks == null) {
			return;
		}
		if ("ABORT".equals(phase.name()) || phase == Phase.END && spec.toggle() != null) {
			for (String look : looks) {
				takeOff(id, look);
			}
			return;
		}
		if (phase != Phase.CAST) {
			return;
		}
		int ticks = p.duration() > 0 ? p.duration() : DEFAULT_TICKS.getOrDefault(spec.id(), 200);
		if (spec.toggle() != null && p.duration() <= 0) {
			ticks = 20 * 60 * 5;
		}
		for (String look : looks) {
			wear(id, look, Mth.clamp(ticks, 20, 20 * 60 * 10));
		}
	}

	private static final Map<String, String[]> LOOKS = new HashMap<>();
	private static final Map<String, Integer> DEFAULT_TICKS = new HashMap<>();

	static {
		LOOKS.put("proud_golden_body", new String[] {"golden_body"});
		LOOKS.put("bright_king_art", new String[] {"golden_body"});
		DEFAULT_TICKS.put("bright_king_art", 400);
		LOOKS.put("great_way_of_buddha", new String[] {"golden_body"});
		LOOKS.put("burning_heart", new String[] {"burning_heart"});
		LOOKS.put("lightning_mark_armor", new String[] {"lightning_armor", "lightning_wings"});
		LOOKS.put("phoenix_bathing_fire", new String[] {"flame_wings"});
		LOOKS.put("wind_thunder_wings", new String[] {"wind_thunder_wings"});
		DEFAULT_TICKS.put("wind_thunder_wings", 30);
		LOOKS.put("lei_zhenzi_wings", new String[] {"lei_zhenzi_wings", "golden_cudgel"});
		LOOKS.put("ice_empress_pincer", new String[] {"ice_claw"});
		LOOKS.put("azure_essence_sword", new String[] {"azure_sword"});
		LOOKS.put("fa_tian_xiang_di", new String[] {"dharma"});
		LOOKS.put("cosmic_giant_form", new String[] {"dharma"});
		DEFAULT_TICKS.put("cosmic_giant_form", 400);
		LOOKS.put("iron_body", new String[] {"iron_plates"});
		LOOKS.put("dharma_form", new String[] {"dharma"});
		DEFAULT_TICKS.put("dharma_form", 600);
		// Worldwalking: the body sinks into the earth while it travels underground and rises at the exit
		LOOKS.put("worldwalking", new String[] {"earth_sink"});
		DEFAULT_TICKS.put("worldwalking", 40);
	}

	/** The view of an entity for this frame (null when it wears nothing). */
	static @Nullable View view(Entity entity, float age) {
		int id = entity.getId();
		Form form = of(id);
		List<Effect> list = EFFECTS.get(id);
		List<String> effects = List.of();
		if (list != null && !list.isEmpty()) {
			long now = AnimClient.ticks();
			list.removeIf(e -> now > e.startTick() + e.ticks());
			if (!list.isEmpty()) {
				List<String> ids = new ArrayList<>(list.size());
				for (Effect effect : list) {
					ids.add(effect.id());
				}
				effects = ids;
			}
		}
		if (form == null && effects.isEmpty()) {
			return null;
		}
		String formId = form == null ? "" : form.id();
		float since = form == null ? 0.0F : AnimClient.ticks() - form.startTick() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return new View(formId, form == null ? 0 : form.stage(), since, effects);
	}

	/** Marks a one-shot beat of a form now (the dragonblood tail sweep on its art's extra event). */
	public static void pulse(int entityId, String key) {
		PULSES.computeIfAbsent(entityId, id -> new HashMap<>(2)).put(key, AnimClient.ticks());
	}

	/** Ticks (with the partial tick) since a beat of an entity's form, or -1 when none is recent. */
	public static float pulseAge(int entityId, String key) {
		Map<String, Long> map = PULSES.get(entityId);
		Long at = map == null ? null : map.get(key);
		if (at == null) {
			return -1.0F;
		}
		float since = AnimClient.ticks() - at + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return since > 40.0F ? -1.0F : since;
	}

	/** Ticks since an effect of an entity began (for grow-in animations), or -1. */
	public static float effectAge(int entityId, String effect) {
		List<Effect> list = EFFECTS.get(entityId);
		if (list == null) {
			return -1.0F;
		}
		for (Effect e : list) {
			if (e.id().equals(effect)) {
				return AnimClient.ticks() - e.startTick();
			}
		}
		return -1.0F;
	}

	/** Ticks left of an effect, or 0. */
	public static int effectLeft(int entityId, String effect) {
		List<Effect> list = EFFECTS.get(entityId);
		if (list == null) {
			return 0;
		}
		for (Effect e : list) {
			if (e.id().equals(effect)) {
				return (int) Math.max(0L, e.startTick() + e.ticks() - AnimClient.ticks());
			}
		}
		return 0;
	}

	static void prune(@Nullable ClientLevel level) {
		if (level == null) {
			FORMS.clear();
			EFFECTS.clear();
			PULSES.clear();
			return;
		}
		PULSES.keySet().removeIf(id -> level.getEntity(id) == null);
		for (Iterator<Integer> it = FORMS.keySet().iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next());
			if (entity == null || entity.isRemoved()) {
				it.remove();
			}
		}
		long now = AnimClient.ticks();
		for (Iterator<Map.Entry<Integer, List<Effect>>> it = EFFECTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, List<Effect>> entry = it.next();
			entry.getValue().removeIf(e -> now > e.startTick() + e.ticks());
			Entity entity = level.getEntity(entry.getKey());
			if (entry.getValue().isEmpty() || entity == null || entity.isRemoved()) {
				it.remove();
			}
		}
	}

	static void clear() {
		FORMS.clear();
		EFFECTS.clear();
		PULSES.clear();
	}

	private ClientForms() {
	}
}
