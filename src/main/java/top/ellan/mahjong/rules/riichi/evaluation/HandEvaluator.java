package top.ellan.mahjong.rules.riichi.evaluation;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;

import java.util.List;

public interface HandEvaluator {
    HandAnalysis analyze(List<Tile> concealedTiles, List<Meld> melds) throws EvaluationException;
}
