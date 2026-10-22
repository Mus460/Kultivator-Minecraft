package dev.kultivator.sekte;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteBus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one way the ten modules write to the hall log (DESIGN 36.3.1, contract C36.L4): the line goes to the existing
 * {@code Halls.log} (the Aula tablet's log, {@code Halls.LOG_SIZE} lines) and to {@link SekteBus#SECT_LOG}, from which
 * module E's elders build the daily report. {@code module} is the writer's id ({@link SekteIds#MODULES}).
 */
public final class SekteLog {

	public static void log(@Nullable MinecraftServer server, @Nullable UUID owner, String module, @Nullable Component line) {
		if (owner == null || line == null) {
			return;
		}
		Halls.log(owner, line);
		if (server != null) {
			SekteBus.SECT_LOG.invoker().onSectLog(server, owner, module == null ? "" : module, line);
		}
	}

	private SekteLog() {
	}
}
