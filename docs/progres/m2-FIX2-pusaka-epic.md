# m2-FIX2 pusaka-epic (audit-epic-pusaka.md)

- [x] 1 Titah menu from seal / `/kultivasi titah` reset selected decree: HeavenDecreeSealItem.openMenu sends arg -1 (client falls back to the selected decree).
- [x] 2 Far giant weapons vanish after quick dimension round trip: TitahMonuments.tickFar compares the player's dimension to FAR_SENT every tick (was every 100 ticks).
- [x] 3 "Bawaan" (OP_RESET) did not refresh seal tooltip: TitahSettings OP_RESET now calls HeavenDecreeSealItem.refreshView.
- [x] 4 BEAT_STOP_ALL 120 collided with Sekte beat: EpicIds.BEAT_STOP_ALL = 250 (above EXTRA_SEKTE_LAST 199); only users are EpicTests and EpicDirector via the constant.
- [x] 5 OP_OPEN never sent: skipped (harmless; the hub card registers TitahScreen directly in HubPages/HubNav, outside my files; casting still needs the seal).
