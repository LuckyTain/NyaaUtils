package cat.nyaa.nyaautils.particle;

import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaautils.I18n;
import cat.nyaa.nyaautils.NyaaUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParticleCommands extends CommandReceiver {
    private NyaaUtils plugin;

    public ParticleCommands(Object plugin, LanguageRepository i18n) {
        super((NyaaUtils) plugin, i18n);
        this.plugin = (NyaaUtils) plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "particle";
    }

    @SubCommand(value = "create", permission = "nu.particles.editor")
    public void commandCreate(CommandSender sender, Arguments args) {
        if (args.length() == 4) {
            ParticleSet set = new ParticleSet();
            set.setAuthor(asPlayer(sender).getUniqueId());
            set.setType(args.nextEnum(ParticleType.class));
            set.setName(args.nextString());
            checkEnabledParticleType(set.getType());
            while (true) {
                int id = plugin.cfg.particleConfig.index++;
                if (!plugin.cfg.particleConfig.particleSets.containsKey(id)) {
                    set.setId(id);
                    plugin.cfg.particleConfig.particleSets.put(id, set);
                    plugin.cfg.save();
                    printParticleSetInfo(sender, set);
                    return;
                }
            }
        } else {
            throw new BadCommandException("manual.particle.create.usage");
        }
    }

    @SubCommand(value = "add", permission = "nu.particles.editor")
    public void commandAdd(CommandSender sender, Arguments args) {
        int id = args.nextInt();
        ParticleSet set = plugin.cfg.particleConfig.particleSets.get(id);
        if (set == null) {
            throw new BadCommandException("user.particle.not_exist", id);
        }
        checkEnabledParticleType(set.getType());
        if (!isAdminOrAuthor(asPlayer(sender), set)) {
            throw new BadCommandException("user.particle.no_permission");
        }
        ParticleLimit limit = plugin.cfg.particlesLimits.get(set.getType());
        Particle particle = args.nextEnum(Particle.class);
        if (!plugin.cfg.particles_enabled.contains(particle.name())) {
            throw new BadCommandException("user.particle.not_enabled", particle.name());
        }
        if (set.contents.size() >= limit.getSet()) {
            throw new BadCommandException("user.particle.add.limit");
        }
        ParticleData data = new ParticleData();
        data.setParticle(particle);
        data.setCount(args.nextInt());
        data.setFreq(args.nextInt());
        data.setOffsetX(args.nextDouble());
        data.setOffsetY(args.nextDouble());
        data.setOffsetZ(args.nextDouble());
        data.setExtra(args.nextDouble());
        if (!particle.getDataType().equals(Void.class)) {
            if (particle.getDataType().equals(Particle.DustOptions.class)) {
                if (args.length() < 12) {
                    msg(sender, "user.particle.add.dust_options", I18n.format("manual.particle.add.usage"));
                    return;
                }
                data.dustOptions_color = args.nextInt();
                data.dustOptions_size = (float) args.nextDouble();
            } else {
                if (args.length() < 11) {
                    msg(sender, "user.particle.add.need_material", I18n.format("manual.particle.add.usage"));
                    return;
                }
                data.setMaterial(args.nextEnum(Material.class));
                if ((particle.getDataType().equals(ItemStack.class) && !data.getMaterial().isItem()) ||
                        (particle.getDataType().equals(BlockData.class) && !data.getMaterial().isBlock())) {
                    msg(sender, "user.particle.invalid_material");
                    return;
                }
            }
        }
        set.contents.add(data);
        plugin.cfg.save();
        printParticleSetInfo(sender, set);
    }

    @SubCommand(value = "remove", permission = "nu.particles.editor")
    public void commandRemove(CommandSender sender, Arguments args) {
        int id = args.nextInt();
        ParticleSet set = plugin.cfg.particleConfig.particleSets.get(id);
        if (set == null) {
            throw new BadCommandException("user.particle.not_exist", id);
        }
        if (!isAdminOrAuthor(asPlayer(sender), set)) {
            throw new BadCommandException("user.particle.no_permission");
        }
        if (args.length() == 4) {
            int index = args.nextInt();
            if (!(index < 0 || index >= set.contents.size())) {
                set.contents.remove(index);
                plugin.cfg.save();
            }
            printParticleSetInfo(sender, set);
        } else {
            plugin.cfg.particleConfig.particleSets.remove(id);
            plugin.cfg.save();
            msg(sender, "user.particle.remove.success", id);
        }
    }

    @SubCommand(value = "set", permission = "nu.particles.player")
    public void commandSet(CommandSender sender, Arguments args) {
        if (args.length() != 4) {
            throw new BadCommandException("manual.particle.set.usage");
        }
        ParticleType type = args.nextEnum(ParticleType.class);
        checkEnabledParticleType(type);
        int id = -1;
        if (!"CLEAR".equalsIgnoreCase(args.top())) {
            id = args.nextInt();
        }
        ParticleSet set = plugin.cfg.particleConfig.particleSets.get(id);
        if (id == -1 || (set != null && type.equals(set.getType()))) {
            plugin.cfg.particleConfig.setParticleSet(asPlayer(sender).getUniqueId(), type, id);
            plugin.cfg.save();
        } else {
            throw new BadCommandException("user.particle.not_exist", id);
        }
    }

    @SubCommand(value = "list", permission = "nu.particles.player")
    public void commandList(CommandSender sender, Arguments args) {
        if (args.length() < 3) {
            throw new BadCommandException("manual.particle.list.usage");
        }
        ParticleType type = args.nextEnum(ParticleType.class);
        int page = args.length() == 4 ? args.nextInt() : 1;
        listParticleSet(sender, page, type, null);
    }

    @SubCommand(value = "toggle", permission = "nu.particles.player")
    public void commandToggle(CommandSender sender, Arguments args) {
        Player player = asPlayer(sender);
        if (plugin.particleTask.bypassPlayers.contains(player.getUniqueId())) {
            plugin.particleTask.bypassPlayers.remove(player.getUniqueId());
            msg(sender, "user.particle.turned_on");
        } else {
            plugin.particleTask.bypassPlayers.add(player.getUniqueId());
            msg(sender, "user.particle.turned_off");
        }
    }

    @SubCommand(value = "my", permission = "nu.particles.player")
    public void commandMy(CommandSender sender, Arguments args) {
        Player player = asPlayer(sender);
        ParticleType type = args.nextEnum(ParticleType.class);
        int page = args.length() == 4 ? args.nextInt() : 1;
        listParticleSet(sender, page, type, player.getUniqueId());
    }

    private void listParticleSet(CommandSender sender, int page, ParticleType type, UUID author) {
        List<ParticleSet> tmp;
        if (author == null) {
            tmp = plugin.cfg.particleConfig.particleSets.values().stream().
                    filter(p -> p.getType().equals(type)).collect(Collectors.toList());
        } else {
            tmp = plugin.cfg.particleConfig.particleSets.values().stream().
                    filter(p -> p.getAuthor().equals(author)).filter(p -> p.getType().equals(type)).collect(Collectors.toList());
        }
        int pageSize = 10;
        int pageCount = (tmp.size() + pageSize - 1) / pageSize;
        if (page < 1 || page > pageCount) {
            page = 1;
        }
        msg(sender, "user.particle.info.page", page, pageCount);
        tmp.stream().skip(pageSize * (page - 1)).limit(pageSize).forEach(set -> printParticleSetInfo(sender, set));
    }

    public boolean isAdminOrAuthor(Player p, ParticleSet set) {
        return p.hasPermission("nu.particles.admin") || p.getUniqueId().equals(set.getAuthor());
    }

    public void printParticleSetInfo(CommandSender sender, ParticleSet set) {
        if (set != null) {
            OfflinePlayer author = Bukkit.getOfflinePlayer(set.getAuthor());
            if (sender instanceof Player) {
                TextComponent msg = new TextComponent(I18n.format("user.particle.info.name", set.getId(), set.getName(), author.getName()));
                msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(I18n.format("user.particle.info.use")).create()));
                msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/nu particle set " + set.getType().name() + " " + set.getId()));
                asPlayer(sender).spigot().sendMessage(msg);
            } else {
                msg(sender, "user.particle.info.name", set.getId(), set.getName(), author.getName());
            }
            for (int i = 0; i < set.contents.size(); i++) {
                ParticleData p = set.contents.get(i);
                String msg = I18n.format("user.particle.info.content", i,
                        p.getParticle().name(), p.getCount(), p.getFreq(),
                        p.getOffsetX(), p.getOffsetY(), p.getOffsetZ(),
                        p.getExtra());
                if (p.getParticle().getDataType().equals(Particle.DustOptions.class)) {
                    sender.sendMessage(msg + I18n.format("user.particle.info.dust_options", p.dustOptions_color,p.dustOptions_size));
                } else if (p.getMaterial() == null) {
                    sender.sendMessage(msg);
                } else {
                    sender.sendMessage(msg + I18n.format("user.particle.info.material", p.getMaterial().name()));
                }
            }
        }
    }

    public void checkEnabledParticleType(ParticleType type) {
        if ((type == ParticleType.PLAYER && !plugin.cfg.particles_type_player) ||
                (type == ParticleType.ELYTRA && !plugin.cfg.particles_type_elytra) ||
                (type == ParticleType.OTHER && !plugin.cfg.particles_type_other)) {
            throw new BadCommandException("user.particle.type.not_enabled", type.name());
        }
    }
}
