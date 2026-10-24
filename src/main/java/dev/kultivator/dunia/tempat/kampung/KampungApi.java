package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.data.Realm;
import dev.kultivator.desa.Tribute;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Villages public face (contract C38.DS1, owner 38DS). Answers come from the village records of {@link KampungData}
 * (written when a village's site anchor first loads), so they hold while the village is unloaded; a village never seen
 * answers null / 0.
 */
public final class KampungApi {

	/** Reach of a pledged village's bell from its record (blocks). */
	static final double IKRAR_JARAK = 48.0D;
	/** An NPC sect whose gate lies within this distance of an unpledged village counts as its protector. */
	static final double SEKTE_JARAK = 640.0D;

	private static volatile @Nullable MinecraftServer server;

	static void server(@Nullable MinecraftServer current) {
		server = current;
	}

	/**
	 * The sect that protects village {@code situsId}: {@code u:<owner uuid>} for a player's hall that pledged it (the
	 * {@code LambangCache} key form of DESIGN 40.4), else {@code npc:<sect id>} for the NPC sect whose gate stands within
	 * 640 blocks, else null.
	 */
	public static @Nullable String sekteDi(long situsId) {
		MinecraftServer s = server;
		if (s == null) {
			return null;
		}
		KampungData.Desa d = KampungData.get(s).cari(situsId);
		if (d == null) {
			return null;
		}
		BlockPos bell = bell(d);
		Tribute.Ikrar ikrar = Tribute.ikrarDi(bell, IKRAR_JARAK);
		if (ikrar != null) {
			return "u:" + ikrar.owner();
		}
		String sect = SekteApi.diplomasi().nearest(bell, -1);
		if (sect != null) {
			BlockPos gate = SekteApi.diplomasi().plannedGate(sect);
			if (gate != null && Math.hypot(gate.getX() - bell.getX(), gate.getZ() - bell.getZ()) <= SEKTE_JARAK) {
				return "npc:" + sect;
			}
		}
		return null;
	}

	/**
	 * Defence strength of village {@code situsId} as a realm ordinal (0 none; 38.R.5 compares it with the player's realm):
	 * a hamlet 0, a village Qi Refining, a market town Foundation, a clan village its patriarch's realm (Foundation to
	 * Nascent Soul); a pledged village with its Pelindung Desa standing guard takes the guard's realm when higher.
	 */
	public static int kekuatan(long situsId) {
		MinecraftServer s = server;
		if (s == null) {
			return 0;
		}
		KampungData.Desa d = KampungData.get(s).cari(situsId);
		if (d == null) {
			return 0;
		}
		int v = switch (d.ukuran) {
			case "klan" -> Klan.ranahPatriark(situsId).ordinal();
			case "desa_besar" -> Realm.FOUNDATION.ordinal();
			case "desa" -> Realm.QI_REFINING.ordinal();
			default -> Realm.MORTAL.ordinal();
		};
		Tribute.Ikrar ikrar = Tribute.ikrarDi(bell(d), IKRAR_JARAK);
		if (ikrar != null && ikrar.guardPresent()) {
			v = Math.max(v, ikrar.guardRealm());
		}
		return v;
	}

	/** The village's bell, else its square. */
	static BlockPos bell(KampungData.Desa d) {
		return d.lonceng.equals(BlockPos.ZERO) ? d.pusat : d.lonceng;
	}

	private KampungApi() {
	}
}
