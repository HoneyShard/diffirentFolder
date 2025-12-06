package pl.skidam.automodpack_loader_core_fabric.loader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import pl.skidam.automodpack_core.GlobalVariables;
import pl.skidam.automodpack_core.loader.LoaderManagerService;
import pl.skidam.automodpack_core.utils.CustomFileUtils;
import pl.skidam.automodpack_core.utils.FileInspection;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.*;

import static pl.skidam.automodpack_core.GlobalVariables.LOGGER;

@SuppressWarnings("unused")
public class LoaderManager implements LoaderManagerService {


    @Override
    public ModPlatform getPlatformType() {
        return ModPlatform.FABRIC;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    private Collection<FileInspection.Mod> modList = new ArrayList<>();
    private int lastLoadingModListSize = -1;

    @Override
    public Collection<FileInspection.Mod> getModList() {
        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();

        if (!modList.isEmpty() && lastLoadingModListSize == mods.size()) {
            return modList;
        }

        lastLoadingModListSize = mods.size();
        Collection<FileInspection.Mod> modList = new ArrayList<>();

        for (var info : mods) {
            try {
                String modID = info.getMetadata().getId();
                Path path = getModPath(modID);
                if (path == null || path.toString().isEmpty()) continue;

                String hash = CustomFileUtils.getHash(path);
                if (hash == null) continue;

                Set<String> providesIDs = new HashSet<>(info.getMetadata().getProvides());
                List<String> dependencies = info.getMetadata().getDependencies().stream()
                        .filter(d -> d.getKind() == ModDependency.Kind.DEPENDS)
                        .map(ModDependency::getModId)
                        .toList();

                FileInspection.Mod mod = new FileInspection.Mod(
                        modID, hash, providesIDs,
                        info.getMetadata().getVersion().getFriendlyString(),
                        path, getModEnvironment(modID), dependencies
                );

                modList.add(mod);
            } catch (Exception ignored) {}
        }

        return this.modList = modList;
    }

    @Override
    public String getLoaderVersion() {
        return FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    private Path getModPath(String modId) {
        if (!isModLoaded(modId)) return null;

        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                if (container.getMetadata().getId().equals(modId)) {
                    FileSystem fs = container.getRootPaths().get(0).getFileSystem();
                    return Path.of(fs.toString());
                }
            }
        } catch (Exception ignored) {}

        LOGGER.error("Could not find jar file for {}", modId);
        return null;
    }

    @Override
    public EnvironmentType getEnvironmentType() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                ? EnvironmentType.CLIENT
                : EnvironmentType.SERVER;
    }

    @Override
    public String getModVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    private EnvironmentType getModEnvironment(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(c -> switch (c.getMetadata().getEnvironment()) {
                    case CLIENT -> EnvironmentType.CLIENT;
                    case SERVER -> EnvironmentType.SERVER;
                    default -> EnvironmentType.UNIVERSAL;
                })
                .orElse(EnvironmentType.UNIVERSAL);
    }
}
