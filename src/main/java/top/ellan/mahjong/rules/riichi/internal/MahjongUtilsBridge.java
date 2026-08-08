package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.evaluation.EvaluationException;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only class that knows mahjong-utils implementation types. Reflection is
 * deliberate: the dependency exposes Kotlin value classes with JVM-mangled
 * names that cannot be referenced safely from Java source.
 */
final class MahjongUtilsBridge {
    private final Object tileCompanion;
    private final Method parseTiles;
    private final Method tileUnbox;
    private final Method furoFactory;
    private final Method furoBox;
    private final Constructor<?> horaOptionsConstructor;
    private final Constructor<?> yakusConstructor;
    private final Method getYakuByName;
    private final Method hora;
    private final Class<?> windClass;
    private final Object[] normalTiles;
    private final Object[] redTiles;
    private final Map<MethodKey, Method> accessorMethods = new ConcurrentHashMap<>();
    private final Map<MethodKey, Method> pointMethods = new ConcurrentHashMap<>();

    MahjongUtilsBridge() {
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

            Class<?> horaOptions = Class.forName("mahjongutils.hora.HoraOptions");
            horaOptionsConstructor = horaOptions.getConstructor(
                    boolean.class, boolean.class, boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class);
            Class<?> yakus = Class.forName("mahjongutils.yaku.Yakus");
            yakusConstructor = yakus.getConstructor(horaOptions);
            getYakuByName = yakus.getMethod("getYaku", String.class);

            windClass = Class.forName("mahjongutils.models.Wind");
            Class<?> horaKt = Class.forName("mahjongutils.hora.HoraKt");
            hora = findHoraMethod(horaKt, horaOptions);

            normalTiles = new Object[TileKind.values().length];
            redTiles = new Object[TileKind.values().length];
            for (TileKind kind : TileKind.values()) {
                normalTiles[kind.ordinal()] = parseSingle(kind.notation());
                redTiles[kind.ordinal()] = !kind.isHonor() && kind.rank() == 5
                        ? parseSingle("0" + kind.notation().charAt(1))
                        : normalTiles[kind.ordinal()];
            }
        } catch (ReflectiveOperationException error) {
            throw new EvaluationException("mahjong-utils 0.7.7 API is incompatible", error);
        }
    }

    Object horaOptions(
            boolean allowOpenTanyao,
            boolean kiriageMangan,
            boolean kazoeYakuman,
            boolean multipleYakuman,
            boolean complexYakuman) {
        try {
            return horaOptionsConstructor.newInstance(
                    false,
                    allowOpenTanyao,
                    true,
                    kiriageMangan,
                    kazoeYakuman,
                    multipleYakuman,
                    complexYakuman);
        } catch (ReflectiveOperationException error) {
            throw failure("could not create scoring options", error);
        }
    }

    Set<Object> extraYaku(Object options, List<String> names) {
        try {
            Object yakus = yakusConstructor.newInstance(options);
            java.util.LinkedHashSet<Object> result = new java.util.LinkedHashSet<>();
            for (String name : names) {
                Object yaku = getYakuByName.invoke(yakus, name);
                if (yaku == null) {
                    throw new EvaluationException("mahjong-utils does not expose expected yaku: " + name);
                }
                result.add(yaku);
            }
            return Set.copyOf(result);
        } catch (ReflectiveOperationException error) {
            throw failure("could not construct situational yaku", error);
        }
    }

    Object score(
            List<Tile> concealedTiles,
            List<Meld> melds,
            Tile winningTile,
            boolean tsumo,
            int bonusDora,
            Wind seatWind,
            Wind roundWind,
            Set<Object> extraYaku,
            Object options) {
        try {
            List<Object> utilsWinningTile = toUtilsTiles(List.of(winningTile), false);
            int winningCode = (Integer) tileUnbox.invoke(utilsWinningTile.getFirst());
            return hora.invoke(
                    null,
                    toUtilsTiles(concealedTiles, false),
                    toUtilsMelds(melds),
                    winningCode,
                    tsumo,
                    bonusDora,
                    utilsWind(seatWind),
                    utilsWind(roundWind),
                    extraYaku,
                    options);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw failure("score evaluation failed", error);
        }
    }

    @SuppressWarnings("unchecked")
    Set<Object> yaku(Object horaResult) {
        return (Set<Object>) invokeNoArgs(horaResult, "getYaku");
    }

    String yakuName(Object yaku) {
        return (String) invokeNoArgs(yaku, "getName");
    }

    int yakuHan(Object yaku) {
        return (Integer) invokeNoArgs(yaku, "getHan");
    }

    int yakuOpenLoss(Object yaku) {
        return (Integer) invokeNoArgs(yaku, "getFuroLoss");
    }

    boolean isYakuman(Object yaku) {
        return (Boolean) invokeNoArgs(yaku, "isYakuman");
    }

    int han(Object horaResult) {
        return (Integer) invokeNoArgs(horaResult, "getHan");
    }

    int fu(Object horaResult) {
        return (Integer) invokeNoArgs(horaResult, "getHu");
    }

    int dealerRon(Object horaResult) {
        return unsignedPoint(invokeNoArgs(horaResult, "getParentPoint"), "getRon");
    }

    int dealerTsumoEach(Object horaResult) {
        return unsignedPoint(invokeNoArgs(horaResult, "getParentPoint"), "getTsumo-");
    }

    int childRon(Object horaResult) {
        return unsignedPoint(invokeNoArgs(horaResult, "getChildPoint"), "getRon");
    }

    int childTsumoDealer(Object horaResult) {
        return unsignedPoint(invokeNoArgs(horaResult, "getChildPoint"), "getTsumoParent");
    }

    int childTsumoChild(Object horaResult) {
        return unsignedPoint(invokeNoArgs(horaResult, "getChildPoint"), "getTsumoChild");
    }

    private List<Object> toUtilsTiles(List<Tile> tiles, boolean normalizeRed) {
        List<Object> result = new ArrayList<>(tiles.size());
        for (Tile tile : tiles) {
            Object converted = !normalizeRed && tile.red()
                    ? redTiles[tile.kind().ordinal()]
                    : normalTiles[tile.kind().ordinal()];
            result.add(converted);
        }
        return result;
    }

    private Object parseSingle(String notation) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<Object> parsed = (List<Object>) parseTiles.invoke(tileCompanion, notation);
        if (parsed.size() != 1) {
            throw new EvaluationException("mahjong-utils could not parse tile: " + notation);
        }
        return parsed.getFirst();
    }

    private List<Object> toUtilsMelds(List<Meld> melds) {
        List<Object> result = new ArrayList<>(melds.size());
        try {
            for (Meld meld : melds) {
                int raw = (Integer) furoFactory.invoke(null, toUtilsTiles(meld.logicalTiles(), true), !meld.open());
                result.add(furoBox.invoke(null, raw));
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw failure("meld conversion failed", error);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object utilsWind(Wind wind) {
        return Enum.valueOf((Class<? extends Enum>) windClass.asSubclass(Enum.class), switch (wind) {
            case EAST -> "East";
            case SOUTH -> "South";
            case WEST -> "West";
            case NORTH -> "North";
        });
    }

    private static Method findHoraMethod(Class<?> horaKt, Class<?> optionsClass) {
        for (Method method : horaKt.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().startsWith("hora-")
                    && parameters.length == 9
                    && parameters[0] == List.class
                    && parameters[1] == List.class
                    && parameters[2] == int.class
                    && parameters[8] == optionsClass) {
                return method;
            }
        }
        throw new EvaluationException("mahjong-utils hora entrypoint was not found");
    }

    private static Method declaredMethod(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private Object invokeNoArgs(Object target, String exactName) {
        try {
            MethodKey key = new MethodKey(target.getClass(), exactName);
            Method method = accessorMethods.get(key);
            if (method == null) {
                method = target.getClass().getMethod(exactName);
                accessorMethods.put(key, method);
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            throw failure("mahjong-utils accessor failed: " + exactName, error);
        }
    }

    private int unsignedPoint(Object target, String prefix) {
        try {
            MethodKey key = new MethodKey(target.getClass(), prefix);
            Method getter = pointMethods.get(key);
            if (getter == null) {
                for (Method method : target.getClass().getMethods()) {
                    if (method.getParameterCount() == 0 && method.getName().startsWith(prefix)) {
                        getter = method;
                        pointMethods.put(key, method);
                        break;
                    }
                }
            }
            if (getter == null) {
                throw new NoSuchMethodException(prefix);
            }
            long value = (Long) getter.invoke(target);
            return Math.toIntExact(value);
        } catch (ReflectiveOperationException | ArithmeticException error) {
            throw failure("mahjong-utils point accessor failed: " + prefix, error);
        }
    }

    private static EvaluationException failure(String message, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : error;
        if (cause instanceof EvaluationException evaluationException) {
            return evaluationException;
        }
        return new EvaluationException(message, cause);
    }

    private record MethodKey(Class<?> owner, String name) {
    }

}
