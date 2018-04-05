package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.earthcomputer.clientcommands.EventManager.Listener;
import net.earthcomputer.clientcommands.core.ClientCommandsLoadingPlugin;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class CoreModSanityCheck implements Listener<PlayerTickEvent> {

	@Override
	public void accept(PlayerTickEvent t) {
		List<String> remainingTasks = new ArrayList<>(ClientCommandsLoadingPlugin.getExpectedTasks());
		if (!remainingTasks.isEmpty()) {
			Collections.sort(remainingTasks);
			CrashReport crashReport = CrashReport.makeCrashReport(new AssertionError(),
					"CLIENT COMMANDS CORE MOD FAILED TO EXECUTE PROPERLY!");
			CrashReportCategory failedCtgy = crashReport.makeCategory("Failed tasks");
			remainingTasks.forEach(task -> failedCtgy.addCrashSection(task, ""));
			throw new ReportedException(crashReport);
		}
	}

	@Override
	public boolean isOneTime() {
		return true;
	}

}
