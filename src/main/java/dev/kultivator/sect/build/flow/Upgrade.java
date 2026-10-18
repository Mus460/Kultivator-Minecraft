package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Naik Templat, the template upgrade (DESIGN 21.18): any larger template on the same stele and rotation. The new plan builds
 * from its own T1 while the old components keep standing and working until a new component needs their cells (the engine
 * dismantles those with a full refund). {@code guaranteeTier} = the old tier is held until the new tier reaches it, so the
 * hall level never drops; reaching it grants {@code sect_upgraded}. The preview and the hold-confirm are the founding ones
 * ({@link Founding}, mode upgrade); the confirm calls {@code EngineApi.beginUpgrade}.
 * <p>
 * Source, growing through the grades: <i>Martial Peak</i> (High Heaven Pavilion, High Heaven Palace, High Heaven Cave
 * Heaven); <i>Battle Through the Heavens</i> (Pan Men, Xiao Gate, the Yan Alliance).
 */
public final class Upgrade {

	/** Why {@code owner} cannot upgrade to {@code template} now, or null. */
	public static @Nullable Component blocker(UUID owner, int template) {
		EngineApi engine = EngineApi.get();
		int current = engine.template(owner);
		if (current <= 0) {
			return Component.translatable("kultivator.sectbuild.upgrade.no_template");
		}
		if (template <= current || !SectTemplates.valid(template)) {
			return Component.translatable("kultivator.sectbuild.upgrade.smaller", SectTemplates.name(current));
		}
		if (template > dev.kultivator.KultivatorConfig.get().sectBuildMaxTemplate) {
			return Component.translatable("kultivator.sectbuild.template.too_large", dev.kultivator.KultivatorConfig.get().sectBuildMaxTemplate);
		}
		return null;
	}

	/** {@code EngineApi.beginUpgrade} with a confirmed preview ticket: the guarantee and the history follow. */
	static @Nullable Component begin(ServerPlayer player, UUID owner, int template, long ticket) {
		EngineApi engine = EngineApi.get();
		int oldTier = Math.max(engine.tier(owner), FlowData.guaranteeTier(owner));
		Component error = engine.beginUpgrade(owner, template, ticket);
		if (error != null) {
			return error;
		}
		if (oldTier > 0) {
			FlowData.setGuarantee(owner, oldTier, FlowData.UNTIL_REACHED, "upgrade");
		}
		FlowData.openHistory(owner, template);
		Halls.log(owner, Component.translatable("kultivator.sectbuild.log.upgrade", SectTemplates.name(template)));
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.started", SectTemplates.name(template), oldTier)
				.withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** A tier finished: an upgrade guarantee that is reached ends with {@code sect_upgraded}; a move guarantee just ends. */
	static void onTier(UUID owner, int tier) {
		int guaranteed = FlowData.guaranteeTier(owner);
		if (guaranteed <= 0 || tier < guaranteed) {
			return;
		}
		String kind = FlowData.guaranteeKind(owner);
		FlowData.clearGuarantee(owner);
		ServerPlayer master = Ceremonies.online(owner);
		if ("upgrade".equals(kind) && master != null) {
			Advancements.grant(master, "sect_upgraded");
			Msg.chat(master, Component.translatable("kultivator.sectbuild.upgrade.reached", tier).withStyle(ChatFormatting.GOLD));
		}
		Halls.refreshBuildings(owner);
	}

	/** Once a minute: an expired move guarantee is dropped (the hall level then follows the scan or the tier). */
	static void tickMinute(UUID owner) {
		if (FlowData.of(owner).contains("guarantee") && FlowData.guaranteeTier(owner) <= 0) {
			FlowData.clearGuarantee(owner);
			Halls.refreshBuildings(owner);
		}
	}

	private Upgrade() {
	}
}
