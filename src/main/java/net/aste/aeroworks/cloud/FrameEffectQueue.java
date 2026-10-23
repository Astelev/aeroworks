package net.aste.aeroworks.cloud;

import java.util.ArrayList;
import java.util.List;

/** Keeps effect draws after the completed world without drawing either twice. */
public final class FrameEffectQueue {
    private final List<Runnable> effects = new ArrayList<>();
    private boolean finished;

    public void begin() {
        effects.clear();
        finished = false;
    }

    public void submit(boolean defer, Runnable draw) {
        if (!defer || finished) draw.run();
        else effects.add(draw);
    }

    public void finish(Runnable world) {
        if (finished) return;
        finished = true;
        var pending = List.copyOf(effects);
        effects.clear();
        try {
            world.run();
        } finally {
            for (var effect : pending) effect.run();
        }
    }
}
