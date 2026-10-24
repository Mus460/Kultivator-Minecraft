# mega-D-TK1 (wave W3 detail pass: pelabuhan + air + gunung) — checkpoint

Scope: jadwal-38-39-40.md W3 row D-TK1 (= files of 38TK-P, 38TK-A, 38TK-G). Design 38.T.3, 38.T.5, 38.T.6, 38.T.15.
Owns: `dunia/tempat/tema/{pelabuhan,air,gunung}/**`, `tools/gen_tempat_tema_{pelabuhan,pelabuhan_cerita,air,gunung}.py`,
`structure/kota_tema/{pelabuhan,air,gunung}/**`, `worldgen/**/kota_tema/{pelabuhan,air,gunung}*`, data
`tempat/{dialog,quest,kabar}/tema_{pelabuhan,air,gunung}*` (+ gunung side quests tema_ziarah_ibu/kuli_sehari/kera_nakal/
rumput_awan), `{permintaan,jadwal}/kota_tema_{pelabuhan,air,gunung}*`, loot `tema/{pelabuhan,gunung}/**`, adv
`tempat/tema_{pelabuhan,air,gunung}_*`, lang `tempat_tema_{pelabuhan,air,gunung}.*`. No Gradle (F3 builds).

## Checklist
- [x] Read schedule, W2 progress (38TK-P/A/G), F2 notes, design 38.T.3/38.T.15
- [x] Survey (tools scratchpad/dtk1/dens.py = interior density per template, iris.py = horizontal slice PNG).
      Findings: W2 code covers every design row of 38.T.3/5/6 (checked storms, Pintu Air, lukisan anchor, herb rank, palang,
      kera -3, raid loss); `uji tema alur` is inti's (generic). Thin spots: pelabuhan interiors (density 0.20-0.33, 7-9 block
      kinds: penginapan, rumah_makan_sup_ikan, kedai_jangkar_patah, rumah_tukang_kapal, balai_dewan, gudang_bersama, mercusuar,
      kedai_teh_ombak); air rumah_perahu_naga/paviliun_teh/halte_pusat/penginapan; gunung rumah_tamu/rumah_pemetik/rumah_makan/
      pondok_pinus/teras rumah. No branch-aware talk after the chains end; air rumours only 19; air story writer lives in
      scratchpad (tka/air_data.py + air_lang.py), not in the repo. NOTE: "stairs" contains "air" (count filters!).
- [x] D1 new `tools/hias_tema.py` (room dressing: fills only empty cells, keeps titik/doorways/stairs free, own seeded
      random, 10 styles inap/kedai/kantor/gudang/bengkel/dapur/kuil/rumah/teh/perahu, centred rugs). Pelabuhan generator:
      registry `ruang()`/`hias()` dressed in simpan(); rumah_merah rooms per storey (GAYA_RUMAH), penginapan 12 rooms,
      kedai jangkar (hall + 6 rooms), rumah makan, kedai teh, gudang 3 floors, balai dewan, kantor 2 floors, pos lintas,
      kuil, gudang jala, rumah seberang. Regenerated: 43 tpl valid, lot rules pass, gzip 1,196,401 (< 1.2 MB)
- [x] D2 air + gunung interior dressing + regenerate + checks
      - [x] air: registry in selesai_distrik (hias also takes city-addressed KotaKanvas ox/oz); inn hall/upper/wing,
            teahouse, tea store, dragon-boat house, boatyard shed, halte office; 68 tpl ok, gzip 1,175,319
      - [x] gunung: registry in potong() (import alias HT: the generator's H is the canvas height!); rumah_gunung every
            storey (rumah / kantor / gudang), bank, kedai teh kabut, kuil kaki, pondok pinus, rumah makan, perpustakaan,
            asrama, rumah tamu; 45 tpl ok, gzip 1,043,155. TplCheck pelabuhan 43/0, air 68/0, gunung 45/0
- [x] D3 air story writer -> repo `tools/gen_tempat_tema_air_cerita.py` (verified byte-identical output vs the scratchpad
      writer before any change), couplets 30 -> 48 (PasanganBait.JUMLAH 48, generator assert + dyn key list), rumours
      19 -> 34 (10 background lines for AIR_ROLES, 5 quest-completion `quest_<id>`); now 509 lang keys
- [x] D4 branch-aware choices in sapa dialogs + rumours
      - [x] pelabuhan: deng_selesai 4 Hei Sha branches (damai/serah/tumpas/fled), new lin_selesai (Zhao lapor/suap/lolos,
            repay bribe bayar_pita 120 once, verdict, Hei Sha free), zhao_selesai_suap/_lapor, gao + priest branch lines;
            rumours 30->38 (5 second voices fired BY ID from the branch choices: KabarTempat.kabar(id) never pools a
            template that exists, so same-pemicu variants would be dead; 3 umum). gen OK: 43 dialogs, 469 keys
      - [x] air: already done in the earlier session (Su Jin/Jin'er/Lu/Wen/Chuan hasil tenang/sepi, Lao Shu flags);
            regenerated, 29 dialogs, 34 rumours, 537 keys
      - [x] gunung: Tie Suo (hasil berkat/berkat_paksa/berkat_suap), Shan Ling (berkat, gunung_paksa, gou_tobat; she is
            gone after suap), Jue Yuan (gou_tobat, gou_suap), Lao Dan (gou_tobat, gou_usir), Yao Po (gou_suap); rumours
            26->33 (3 Gou second voices fired by id + 4 umum). `--cerita` run: 43 dialogs, 446 keys
- [x] D5 checks: scratchpad dtk1/cek_dialog.py over 115 theme dialogs (clips vs GerakId, props vs PropId, lang keys,
      fired rumour ids) 0 errors; lang pairs identical (pelabuhan 469, air 537, gunung 446); every literal
      "kultivator.*" key in tema/{pelabuhan,air,gunung} Java exists; no LightningBolt/weather/time/setBlock in owned Java;
      all touched files LF. NBT not regenerated after D2 (TplCheck 43/68/45 ok then), so no new template check needed.

## Langkah berikutnya
Done. F3: run mergeLang + Gradle; nothing else needed from D-TK1.

## Open problems
- Hei Sha body is in-memory (38TK-P note): a restart during `duel` re-summons via Deng's dialog; `nasib` has fallbacks.

## Butuh dari modul lain
(none yet)
