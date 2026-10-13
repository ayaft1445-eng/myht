# Иконки брони

`render_icon.py` рисует иконку предмета прямо из `.blockymodel` и текстуры
(ортогональный вид спереди-сбоку, 64×64, без полупрозрачных пикселей, с тёмным контуром).

Нужны Python 3, Pillow и NumPy. Перерисовать все иконки после замены моделей:

```sh
R=src/main/resources
for s in White Red; do for p in Head Chest Hands Legs; do
  pitch=25; [ $p = Legs ] && pitch=15
  python3 tools/render_icon.py $R/Common/Items/Armors/$s/$p.blockymodel \
    $R/Common/Items/Armors/$s/${p}_Texture.png \
    $R/Common/Icons/ItemsGenerated/Armor_${s}_$p.png 64 -35 $pitch
done; done
```
