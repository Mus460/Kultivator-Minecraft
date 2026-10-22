# sekte:R progress (Modul R, Hubungan antar-murid, DESIGN 36.2.2 + 36.4 R)

Run 1 (2026-10-22): STATUS DONE (javac 25 clean for all R files, no Gradle).

## Checklist
- [x] 1 Read core API + existing disciple/duel/hub/scene code
- [x] 2 Server sekte/hubungan/: HubunganConfig, TabelPasangan (hall section sekte_hubungan), Kecocokan, GiliranSosial (tea turn,
      rites, lovers sync), Upacara (oath, blessing, forbid, requests), EfekIkatan (DiscipleHooks), DuelKehormatan, Hubungan
      (init, HubunganService, one change funnel, bus listeners, scroll info, Jaring data), HubunganCommands, HubunganPayloads
- [x] 3 SekteContent: R line enabled
- [x] 4 Client client/sekte/hubungan/: HubunganClient (receivers, tab, hub card, pose 18 + teacup prop, scenes 110-112),
      HubunganTab, JaringScreen, GelembungIkatan (bubbles + lovers' petals), HubunganColors; SekteContentClient: R line enabled
- [x] 5 Assets: tools/gen_sekte_hubungan_assets.py -> textures/sekte/hubungan/ikon.png (8 bubbles, checked by eye), cahaya.png,
      textures/item/fx/sekte_hubungan_celadon.png + _tea.png, models/item/fx + items/fx sekte_hubungan_teacup.json;
      data/kultivator/advancement/sekte/hubungan_sahabat.json, hubungan_saingan.json
- [x] 6 Lang lang-src/sekte_hubungan.{id_id,en_us}.json: 181 identical keys, no duplicates with other fragments, placeholders equal
      (written by scratchpad/sekteR/lang_r.py)
- [x] 7 javac (scratchpad/sekteR/jc.sh) clean; all new files LF

## Decisions
- Pair key soulA|soulB (string order). Pair: a, k, h(4), m, t, l, dd/ds/dm (day caps +-15 call, +-25 day), w wound, ban, po.
- Duel result read from DuelSessions.byEntity polling + health ratio (no dependency on T's DuelEndListeners).
- Rites use ACT_SOCIAL (cups) at the stele (not E's ACT_BOW). ACT_SOCIAL is standing (core: only act 13 sits).
- Scenes: 110 oath (SK_GOLD, M_GLORY), 111 Dao companions (SK_DAWN, M_GENTLE), 112 grudge settled (stamp).
- No hall -> no table -> R does nothing for that master (tab says so).

## For the finish step / report
- In game: check teacup prop orientation (Prop pitch 66 deg, scale 0.55) and bubble size; `uji perguruan hubungan temu a b`.
- Guide text request (lead): c15.sahabat body line 3 -> "Lihat Jaring Hubungan di hub Sekte atau tab Hubungan di gulungan murid."
- New voice lines proposed in report (oath, tea, grudge, beloved).

## Next step
None (job finished).
