# Progres detail:B (Naga & Binatang detail pass, DESIGN 34 ideas 57-58)

Run 2 (2026-10-22 14:18). Run 1 (06:52-07:13, before the checkpoint protocol) left
uncommitted work: NagaStrikes, BodyGuards, DarahJiaoItem, NagaFeats, NagaLines, NagaVoice,
5 new fx/naga item models + textures, and edits in 17 naga files. No progress file from run 1.

## Checklist
- [x] 0 audit run-1 work (diffs + new files): compiles, design complete
- [x] 1 finish run-1 gaps: lang keys, 8 advancement JSONs, help topics
- [x] 2 voice: naga.* events via NagaVoice with fallback
- [x] 3 lang: dunia_naga en/id identical keys (298 each), scratchpad/b3/lang_add.py is idempotent
- [x] 4 polish: Darah Jiao vial tinted per kind; sky-event reactions of Jiao/wolves/foxes; realm-up remark
- [x] 5 uji: naga langit <meteor|gerhana|bulan> added (amuk/jurus/jaga/darah/ucap/gila from run 1)
- [x] 6 self-review: javac (scratchpad/b/jc.sh) 0 errors over all naga main+client files; key check clean

## Files touched in run 2
lang-src/dunia_naga.{en_us,id_id}.json; data/kultivator/advancement/dunia/{jiao_three,darah_jiao,jiao_storm,
jiao_rider,true_dragon,yinglong,hufa,body_guard}.json; tools/gen_dunia_naga_models.py; items/darah_jiao.json,
models/item/darah_jiao.json, textures/item/darah_jiao_blood.png (regenerated); dunia/naga/{DarahJiaoItem,
NagaLines,WildDragons,TamedJiao,NagaCommands}.java.

## Next step
None: done. Finish agent compiles; owner tests in game.

## Open problems
- VoiceEvents (not B's file) does not register voice.naga.* yet, so the fallback lines play until it does.
- docs/PANDUAN.md ch. 64 and dunia_panduan (not B's files) do not mention Amukan/Darah Jiao/Penjaga Raga;
  their existing c14.naga / c14.evolusi steps are still accurate (8 s contest, 25 %, Hu Fa 30 %).

## Run 1 work (kept, audited)
Amukan Naga (enrage at 50 %), Gila Darah (blood eclipse frenzy), twin-moon calm, awe of Immortal Ascension+,
kind skills Pusaran Samudra / Tirai Awan / Hujan Petir Langit (wild + tamed tier 5+), resting coil, ride
remarks, NagaLines dialogue, NagaVoice, BodyGuards (Penjaga Raga), DarahJiaoItem, NagaFeats, NagaStrikes
telegraphs, danger HUD, sky grades, tail lash, great horns, rage eyes, star ruff, moon orb, nameplates,
uji naga amuk/jurus/jaga/darah/ucap/gila.
