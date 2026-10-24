package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sosok.gerak.GerakPengendali;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.GerakId;
import org.jetbrains.annotations.Nullable;

/**
 * The body language of module K's people, now played by the shared Gerak system (DESIGN 40.5.10, 40.7.2): the old
 * flags of {@link KultivatorNpc} become clips of the catalog, and the idle stances by role are Gerak's stance table
 * ({@code SikapTabel}, clips 108-121). A clip the server sets through {@code SosokApi.gerak} (schedules of DESIGN 38,
 * {@code DATA_GERAK}) always wins over a flag; the controller asks {@link #klip} only when the server sends none.
 * <ul>
 * <li>{@code FLAG_BOW}: 32 {@code hormat}, the cupped-fist salute (baoquan) with a bow from the hips; civilian women
 * curtsy (wanfu, chosen by the clip's own variant rule).</li>
 * <li>{@code FLAG_GAZE}: 56 {@code tatap_langit}, the head tips back to the sky; talking while gazing points up
 * (variant {@code V_TATAP_TUNJUK}).</li>
 * <li>{@code FLAG_WORK} (standing still): the smith's hammer on the anvil (25), the tea master's endless pour (22 loop),
 * the townsfolk's morning exercise (65), then a clip for each trade (the cook stirs, the fisher hauls the net, the
 * captain scans the sea, the caravan master leads the camel, the painter paints the cave wall...), and by group: the
 * scribe's brush (27), the monk's prayer (120), the guard at his post (15), the elder's slow exercise (65), the
 * cultivator's forms (172), the child's top (70), the sweeping broom (24), the merchants' abacus (29).</li>
 * <li>{@code FLAG_TALK}: 38 {@code bicara_tangan}, the speaking hand and a nodding head (the orator's sweep
 * {@code V_BICARA_MEGAH} for the auctioneers and the lecturer); it plays on the upper body while walking.</li>
 * </ul>
 * The weights, fades and per-body smoothing live in {@code GerakPengendali} (in 3/out 4 for the salute, 6/6 for the
 * loops), so the old per-NPC blend map is gone.
 */
public final class NpcPoses {

	/**
	 * The clip a flag asks for, as a packed {@code GerakId.pack(klip, varian)} value, or 0 when no flag drives the body
	 * (the stance table then decides). {@code walking} is true while the NPC walks (work never plays then).
	 */
	public static int klip(KultivatorNpc npc, boolean walking) {
		int flags = npc.flags();
		if (flags == 0) {
			return 0;
		}
		NpcRole role = npc.role();
		boolean talk = (flags & KultivatorNpc.FLAG_TALK) != 0;
		boolean gaze = (flags & KultivatorNpc.FLAG_GAZE) != 0;
		if ((flags & KultivatorNpc.FLAG_BOW) != 0) {
			return GerakId.pack(GerakId.HORMAT, 0);
		}
		if (gaze) {
			return GerakId.pack(GerakId.TATAP_LANGIT, talk ? GerakId.V_TATAP_TUNJUK : 0);
		}
		if ((flags & KultivatorNpc.FLAG_WORK) != 0 && !walking) {
			return kerja(role);
		}
		if (talk) {
			boolean grand = role == NpcRole.JURU_LELANG || role == NpcRole.PENCERAMAH || role == NpcRole.JURU_LELANG_ABADI;
			return GerakId.pack(GerakId.BICARA_TANGAN, grand ? GerakId.V_BICARA_MEGAH : 0);
		}
		return 0;
	}

	/**
	 * The work clip of a role: the old hand-written work poses first, then a fitting standing clip for every role of
	 * DESIGN 38 and 38.T (none of them needs a seat: seated work comes from the schedules through {@code DATA_GERAK}),
	 * then a sensible clip per group.
	 */
	private static int kerja(NpcRole role) {
		int klip = switch (role) {
			case PANDAI_PUSAKA, EMPU, PANDAI_DESA -> GerakId.PALU_LANDASAN;
			case PEMILIK_TEH, AHLI_TEH, PELAYAN -> 0;
			case WARGA -> GerakId.TAI_CHI_PAGI;
			case PENCERAMAH, PENCERAMAH_FANA -> GerakId.MENGAJAR;
			case ALKEMIS -> GerakId.RACIK_HERBA;
			case PEDAGANG_PIL -> GerakId.GULUNG_PIL;
			case TABIB -> GerakId.TUMBUK_HERBA;
			case KOKI -> GerakId.MASAK;
			case PELAYAN_SEKTE -> GerakId.SAPU;
			case PUSTAKAWAN, PENENUN_SUTRA -> GerakId.RAK_SUSUN;
			case PEMOTONG_BATU, TUKANG_KAPAL -> GerakId.PALU_LUNAS;
			case PEDAGANG_BINATANG -> GerakId.BERI_MAKAN_BINATANG;
			case HAKIM, SYAHBANDAR, PETUGAS_BEA, SASTRAWAN, PETUGAS_GILDA -> GerakId.MENULIS;
			case NAKHODA, NAKHODA_LAUT, PENJAGA_SUAR -> GerakId.TEROPONG_LAUT;
			case KULI -> GerakId.JALAN_BAWA;
			case KULI_GUNUNG -> GerakId.DAKI_PIKUL;
			case PETANI -> GerakId.MENCANGKUL;
			case PENJAGA_KEBUN, PEMELIHARA_ULAT -> GerakId.MEMANEN;
			case NELAYAN -> GerakId.TARIK_JALA;
			case PENJUAL_IKAN -> GerakId.JEMUR_IKAN;
			case PELAUT -> GerakId.IKAT_TALI;
			case PENDAYUNG -> GerakId.DAYUNG_YULOH;
			case TUKANG -> GerakId.PERBAIKI_PALU;
			case PELUKIS_GUA -> GerakId.LUKIS_DINDING;
			case PENARI_GURUN -> GerakId.TARI_PUTAR;
			case PEMETIK_TEH -> GerakId.PETIK_TEH;
			case PEMETIK_HERBA -> GerakId.PETIK_HERBA_TEBING;
			case PENJAGA_LIFT -> GerakId.PUTAR_KATROL;
			case KAFILAH_BASHI, PENUNTUN_UNTA -> GerakId.PIMPIN_UNTA;
			case PEDAGANG_BAZAR -> GerakId.TAWAR_BAZAR;
			case JAGAL_ARENA, GLADIATOR_IBLIS -> GerakId.LATIHAN_TINJU;
			case MURID_SEKTE_LUAR, MURID_KLAN -> GerakId.LATIHAN_PEDANG_1;
			case JUARA_SEKTE, MURID_SEKTE_DALAM -> GerakId.LATIHAN_PEDANG_2;
			case PENJAGA_KITAB_TERLARANG -> GerakId.MEMBACA;
			case PENGEMIS -> GerakId.SIKAP_TUNDUK;
			case TAWANAN -> GerakId.BERLUTUT_RANTAI;
			case PEZIARAH, PENDETA_LAUT, PENDETA_AIR, KEPALA_BIARA, BIKSU_GUNUNG -> GerakId.SIKAP_DOA;
			case PEMBUNUH_BAYARAN -> GerakId.SIKAP_LIPAT_TANGAN;
			default -> -1;
		};
		if (klip == 0) {
			// the tea house: the endless pour of the old tea master's pose
			return GerakId.pack(GerakId.TUANG_TEH, GerakId.V_TUANG_TEH_LOOP);
		}
		if (klip > 0) {
			return GerakId.pack(klip, 0);
		}
		klip = switch (role.kelompok) {
			case CENDEKIA, PEJABAT -> GerakId.MENULIS;
			case RAHIB -> GerakId.SIKAP_DOA;
			case PENJAGA -> GerakId.BERDIRI_JAGA;
			case TETUA -> GerakId.TAI_CHI_PAGI;
			case KULTIVATOR -> GerakId.LATIH_JURUS;
			case ANAK -> GerakId.MAIN_GASING;
			case PEKERJA -> GerakId.SAPU;
			default -> GerakId.HITUNG_SEMPOA;
		};
		return GerakId.pack(klip, 0);
	}

	/**
	 * The NPC's body-language snapshot this frame, or null when nothing drives the model (kept for callers of the old
	 * API: it runs the Gerak controller, which also stores the snapshot in the render state).
	 */
	public static @Nullable CastPoses.Snapshot snapshot(KultivatorNpc npc, DiscipleRenderState state, float age) {
		return GerakPengendali.evaluasi(npc, state, age);
	}

	/** Forgets every body's animation state (world change). */
	public static void clear() {
		GerakPengendali.kosongkan();
	}

	private NpcPoses() {
	}
}
