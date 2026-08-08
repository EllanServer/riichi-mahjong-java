package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test-only differential oracle for the formerly used shanten backend. */
final class MahjongUtilsShantenOracle {
    private final Object tileCompanion;
    private final Method parseTiles;
    private final Method tileUnbox;
    private final Method furoFactory;
    private final Method furoBox;
    private final Method shanten;
    private final Method getShantenInfo;
    private final Method getShantenNumber;
    private final Method getAdvance;
    private final Object[] normalTiles = new Object[TileKind.values().length];
    private final Map<Integer, TileKind> tileKindsByCode;

    MahjongUtilsShantenOracle() {
        try {
            Class<?> tileClass = Class.forName("mahjongutils.models.Tile");
            Class<?> tileCompanionClass = Class.forName("mahjongutils.models.Tile$Companion");
            tileCompanion = tileClass.getField("Companion").get(null);
            parseTiles = tileCompanionClass.getMethod("parseTiles", String.class);
            tileUnbox = declaredMethod(tileClass, "unbox-impl");

            Class<?> furoClass = Class.forName("mahjongutils.models.Furo");
            Class<?> furoKt = Class.forName("mahjongutils.models.FuroKt");
            furoFactory = furoKt.getMethod("Furo", List.class, boolean.class);
            furoBox = declaredMethod(furoClass, "box-impl", int.class);

            Class<?> shantenKt = Class.forName("mahjongutils.shanten.ShantenKt");
            shanten = shantenKt.getMethod("shanten", List.class, List.class);
            Class<?> unionResult = Class.forName("mahjongutils.shanten.UnionShantenResult");
            getShantenInfo = unionResult.getMethod("getShantenInfo");
            Class<?> shantenType = Class.forName("mahjongutils.shanten.Shanten");
            getShantenNumber = shantenType.getMethod("getShantenNum");
            Class<?> withoutGot = Class.forName("mahjongutils.shanten.ShantenWithoutGot");
            getAdvance = withoutGot.getMethod("getAdvance");

            HashMap<Integer, TileKind> reverse = new HashMap<>();
            for (TileKind kind : TileKind.values()) {
                Object parsed = parseSingle(kind.notation());
                normalTiles[kind.ordinal()] = parsed;
                reverse.put((Integer) tileUnbox.invoke(parsed), kind);
            }
            tileKindsByCode = Map.copyOf(reverse);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("mahjong-utils shanten oracle is incompatible", error);
        }
    }

    Evaluation evaluate(List<Tile> concealed, List<Meld> melds) {
        try {
            Object result = shanten.invoke(null, toUtilsTiles(concealed), toUtilsMelds(melds));
            Object info = getShantenInfo.invoke(result);
            int number = (Integer) getShantenNumber.invoke(info);
            if (number != 0 || concealed.size() != 13 - melds.size() * 3) {
                return new Evaluation(number, Set.of());
            }
            if (!getAdvance.getDeclaringClass().isInstance(info)) {
                throw new AssertionError("zero-shanten undrawn hand has no backend advance set");
            }
            @SuppressWarnings("unchecked")
            Set<Object> rawAdvances = (Set<Object>) getAdvance.invoke(info);
            EnumSet<TileKind> advances = EnumSet.noneOf(TileKind.class);
            EnumMap<TileKind, Integer> physicalCopies = new EnumMap<>(TileKind.class);
            concealed.forEach(tile -> physicalCopies.merge(tile.kind(), 1, Integer::sum));
            melds.stream().flatMap(meld -> meld.logicalTiles().stream())
                    .forEach(tile -> physicalCopies.merge(tile.kind(), 1, Integer::sum));
            for (Object raw : rawAdvances) {
                TileKind kind = tileKindsByCode.get((Integer) tileUnbox.invoke(raw));
                if (kind == null) throw new AssertionError("backend returned an unknown tile code");
                if (physicalCopies.getOrDefault(kind, 0) < 4) advances.add(kind);
            }
            return new Evaluation(number, Set.copyOf(advances));
        } catch (InvocationTargetException error) {
            throw new AssertionError("mahjong-utils shanten oracle rejected a generated hand", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("mahjong-utils shanten oracle invocation failed", error);
        }
    }

    private List<Object> toUtilsTiles(List<Tile> tiles) {
        List<Object> result = new ArrayList<>(tiles.size());
        for (Tile tile : tiles) result.add(normalTiles[tile.kind().ordinal()]);
        return result;
    }

    private List<Object> toUtilsMelds(List<Meld> melds) throws ReflectiveOperationException {
        List<Object> result = new ArrayList<>(melds.size());
        for (Meld meld : melds) {
            int raw = (Integer) furoFactory.invoke(null, toUtilsTiles(meld.logicalTiles()), !meld.open());
            result.add(furoBox.invoke(null, raw));
        }
        return result;
    }

    private Object parseSingle(String notation) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<Object> parsed = (List<Object>) parseTiles.invoke(tileCompanion, notation);
        if (parsed.size() != 1) throw new AssertionError("could not parse oracle tile " + notation);
        return parsed.getFirst();
    }

    private static Method declaredMethod(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    record Evaluation(int shanten, Set<TileKind> waits) {
    }
}
