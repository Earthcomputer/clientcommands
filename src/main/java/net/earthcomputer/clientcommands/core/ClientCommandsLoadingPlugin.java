package net.earthcomputer.clientcommands.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

public class ClientCommandsLoadingPlugin implements IFMLLoadingPlugin {

	static final Set<String> EXPECTED_TASKS = new HashSet<>();

	static {
		EXPECTED_TASKS.add("loadCoreMod");
	}

	public static Set<String> getExpectedTasks() {
		return EXPECTED_TASKS;
	}

	@Override
	public String[] getASMTransformerClass() {
		EXPECTED_TASKS.remove("loadCoreMod");
		return new String[] { ProxyTransformer.class.getName(), NetHandlerTransformer.class.getName(),
				IntegratedServerRaceFixTransformer.class.getName() };
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
