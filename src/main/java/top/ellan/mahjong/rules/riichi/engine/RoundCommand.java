package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.Objects;

public sealed interface RoundCommand
        permits RoundCommand.Draw, RoundCommand.Discard, RoundCommand.Respond,
        RoundCommand.DeclareSelfKan, RoundCommand.DeclareNineTerminals, RoundCommand.DeclareTsumo {

    PlayerId player();

    record Draw(PlayerId player) implements RoundCommand {
        public Draw { Objects.requireNonNull(player, "player"); }
    }

    record Discard(PlayerId player, TileId tile, boolean declareRiichi) implements RoundCommand {
        public Discard {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(tile, "tile");
        }
    }

    record Respond(PlayerId player, Reaction reaction) implements RoundCommand {
        public Respond {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(reaction, "reaction");
        }
    }

    record DeclareSelfKan(PlayerId player, TileKind kind) implements RoundCommand {
        public DeclareSelfKan {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(kind, "kind");
        }
    }

    record DeclareNineTerminals(PlayerId player) implements RoundCommand {
        public DeclareNineTerminals { Objects.requireNonNull(player, "player"); }
    }

    record DeclareTsumo(PlayerId player) implements RoundCommand {
        public DeclareTsumo { Objects.requireNonNull(player, "player"); }
    }
}
