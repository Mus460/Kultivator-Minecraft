package dev.kultivator.technique.spec.run;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.technique.Techniques;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The session part of the codex sync (DESIGN 22.12.10): {@code sessionExtra.teknik}, rewritten every 10 ticks and only
 * marked dirty when it changed. Keys (absolute game ticks; the client compares with its level time):
 * <ul>
 * <li>{@code gcd}, {@code lock}: global cooldown and ultimate lockout ends;</li>
 * <li>{@code ch {id: {r ready, m max, n next recharge}}}: CHARGES;</li>
 * <li>{@code act [{k kind, id, e end, r radius}]}: lasting effects; kinds form, domain, field, aura, toggle, channel,
 * charge (e = release tick), stage ({@code s} stage, e = window end), dharma, dao_domain;</li>
 * <li>{@code sh [pool, max]}: Perisai Qi;</li>
 * <li>{@code kh}: the Kehancuran page ({@code Kehancuran.selfStatus});</li>
 * <li>{@code free}: the {@code uji teknik bebas} bypass;</li>
 * <li>{@code displays}: false when the server's {@code techniqueDisplays} is off.</li>
 * </ul>
 */
public final class TeknikSync {

	public static final String KEY = "teknik";

	/** Rebuilds the player's {@code sessionExtra.teknik}; marks the data dirty only when something changed. */
	public static void write(ServerPlayer player, CultivationData data) {
		CompoundTag tag = new CompoundTag();
		long[] times = TechniqueRunner.sessionTimes(player);
		long now = player.level().getGameTime();
		if (times[0] > now) {
			tag.putLong("gcd", times[0]);
		}
		if (times[1] > now) {
			tag.putLong("lock", times[1]);
		}
		Map<String, long[]> charges = TechniqueRunner.chargeState(player);
		if (!charges.isEmpty()) {
			CompoundTag ch = new CompoundTag();
			charges.forEach((id, state) -> {
				CompoundTag entry = new CompoundTag();
				entry.putInt("r", (int) state[0]);
				entry.putInt("m", (int) state[1]);
				entry.putLong("n", state[2]);
				ch.put(id, entry);
			});
			tag.put("ch", ch);
		}
		ListTag active = new ListTag();
		String form = Forms.form(player);
		if (form != null) {
			active.add(entry("form", form, Forms.until(player), (float) Forms.scale(player)));
		}
		for (Fields.Area area : Fields.of(player)) {
			String kind = switch (area.kind) {
				case DOMAIN -> "domain";
				case AURA -> "aura";
				default -> "field";
			};
			active.add(entry(kind, area.source.spec.id(), area.until, (float) area.radius));
		}
		for (Caster cast : TechniqueRunner.activeOf(player)) {
			if (cast.toggled) {
				active.add(entry("toggle", cast.spec.id(), 0L, 0.0F));
			}
			if (cast.channelUntil > now) {
				active.add(entry("channel", cast.spec.id(), cast.channelUntil, 0.0F));
			}
			if (cast.charging) {
				CompoundTag charge = entry("charge", cast.spec.id(), cast.chargeUntil, 0.0F);
				charge.putInt("t", cast.spec.charge() == null ? 0 : cast.spec.charge().ticks());
				active.add(charge);
			}
			if (cast.stageWindowUntil > now) {
				CompoundTag stage = entry("stage", cast.spec.id(), cast.stageWindowUntil, 0.0F);
				stage.putInt("s", cast.stage);
				active.add(stage);
			}
		}
		if (data.dharmaActive()) {
			active.add(entry("dharma", "dharma_form", 0L, 0.0F));
		}
		double[] domain = Techniques.legacyDomain(player);
		if (domain[0] > now) {
			active.add(entry("dao_domain", "dao_domain", (long) domain[0], (float) domain[1]));
		}
		if (!active.isEmpty()) {
			tag.put("act", active);
		}
		double[] shield = QiShield.shieldOf(player);
		if (shield[0] > 0.0D) {
			ListTag sh = new ListTag();
			sh.add(net.minecraft.nbt.FloatTag.valueOf((float) shield[0]));
			sh.add(net.minecraft.nbt.FloatTag.valueOf((float) shield[1]));
			tag.put("sh", sh);
		}
		tag.put("kh", Kehancuran.selfStatus(player));
		if (TechniqueRunner.bypass(player)) {
			tag.putBoolean("free", true);
		}
		if (!dev.kultivator.KultivatorConfig.get().techniqueDisplays) {
			// the server's techniqueDisplays switch, read by the client's SpecFx (sent only when off)
			tag.putBoolean("displays", false);
		}
		CompoundTag session = data.sessionExtra();
		if (!tag.equals(session.getCompoundOrEmpty(KEY))) {
			session.put(KEY, tag);
			Cultivators.dirty(player);
		}
	}

	private static CompoundTag entry(String kind, String id, long end, float radius) {
		CompoundTag tag = new CompoundTag();
		tag.putString("k", kind);
		tag.putString("id", id);
		if (end > 0L) {
			tag.putLong("e", end);
		}
		if (radius > 0.0F) {
			tag.putFloat("r", radius);
		}
		return tag;
	}

	private TeknikSync() {
	}
}
