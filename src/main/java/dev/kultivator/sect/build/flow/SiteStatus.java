package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What a site is doing right now, in words (DESIGN 21.21 status line, 21.17 Aula line, 21.0 user decision 4): the open
 * component and its progress, and above all what the next building still needs ("Masih kurang: 32 Batu, 12 Kayu"). The
 * next building is the first queued component of the tier being built; its shortfall is its bill minus the Gudang.
 */
public final class SiteStatus {

	/** The open component, or null. */
	public static @Nullable ComponentView open(List<ComponentView> components) {
		for (ComponentView component : components) {
			if (component.state() == ComponentView.STATE_OPEN) {
				return component;
			}
		}
		return null;
	}

	/** The first component still waiting for its bill (placement order), or null. */
	public static @Nullable ComponentView next(List<ComponentView> components) {
		for (ComponentView component : components) {
			if (component.state() == ComponentView.STATE_QUEUED) {
				return component;
			}
		}
		return null;
	}

	/** What the next building still needs beyond the Gudang (empty when it is covered or there is none). */
	public static Bill nextShortfall(UUID owner) {
		ComponentView next = next(EngineApi.get().components(owner));
		return next == null ? Bill.EMPTY : next.bill().minus(Ledger.INSTANCE.balance(owner));
	}

	/** The translation key of a site status ({@code SiteView.STATUS_*}). */
	public static String statusKey(int status) {
		return "kultivator.sectbuild.status." + Math.max(0, Math.min(SiteView.STATUS_PREPARING, status));
	}

	/** The status in words, with its detail (shortfall, blocked cells, open component). */
	public static MutableComponent statusText(UUID owner, SiteView site) {
		int status = site.status();
		if (status == SiteView.STATUS_WAITING_FUNDS) {
			Bill need = nextShortfall(owner);
			return Component.translatable(statusKey(status), need.isEmpty() ? Component.literal("…") : Fmt.joined(need, 3));
		}
		if (status == SiteView.STATUS_BLOCKED) {
			return Component.translatable(statusKey(status), site.blocked());
		}
		if (status == SiteView.STATUS_RUNNING) {
			ComponentView open = open(EngineApi.get().components(owner));
			if (open != null) {
				return Component.translatable("kultivator.sectbuild.status.running_on", open.name(), Fmt.percent(open.progress(), 1.0D));
			}
		}
		return Component.translatable(statusKey(status), "");
	}

	/** The colour of a status (green running, gold waiting, grey paused, red blocked). */
	public static ChatFormatting color(int status) {
		return switch (status) {
			case SiteView.STATUS_RUNNING, SiteView.STATUS_COMPLETE -> ChatFormatting.GREEN;
			case SiteView.STATUS_WAITING_FUNDS -> ChatFormatting.GOLD;
			case SiteView.STATUS_BLOCKED, SiteView.STATUS_STELE -> ChatFormatting.RED;
			default -> ChatFormatting.GRAY;
		};
	}

	/**
	 * The Aula tablet's construction line (DESIGN 21.17): "Sekte Kelas Dua · Tingkat 3/9 · Menunggu bahan: 32 Batu", or
	 * null without a template.
	 */
	public static @Nullable Component line(UUID owner) {
		EngineApi engine = EngineApi.get();
		int template = engine.template(owner);
		if (template <= 0) {
			return null;
		}
		SiteView site = engine.site(owner);
		int tier = engine.tier(owner);
		MutableComponent line = Component.translatable("kultivator.sectbuild.aula_line", SectTemplates.name(template), tier, SectTemplates.TIERS);
		if (site != null) {
			line.append(Component.literal(" · "));
			line.append(statusText(owner, site).withStyle(color(site.status())));
		}
		return line;
	}

	private SiteStatus() {
	}
}
