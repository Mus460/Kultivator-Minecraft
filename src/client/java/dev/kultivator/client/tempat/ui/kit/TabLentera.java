package dev.kultivator.client.tempat.ui.kit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The tab list of a kit-38 sheet (DESIGN 38.U.2): id, label, icon and lock reason per tab. Since 43 the Gulungan draws the
 * tabs as the page's sub bar ({@code TabBar}), so this class only holds the list ({@code LayarTempatDasar.subs()}); the
 * old lantern column was removed by 43-P1.
 */
public final class TabLentera {

	/** One tab: id (sheet tab id), label, icon slot inside the lantern, lock reason (null = open). */
	public record Tab(String id, Component label, int ikon, @Nullable Component kunci) {
	}

	private List<Tab> tabs = new ArrayList<>();

	public void setTabs(List<Tab> tabs) {
		this.tabs = tabs;
	}

	public List<Tab> tabs() {
		return tabs;
	}

	public @Nullable Tab tab(int index) {
		return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
	}

	public @Nullable Tab cari(String id) {
		for (Tab tab : tabs) {
			if (tab.id().equals(id)) {
				return tab;
			}
		}
		return null;
	}
}
