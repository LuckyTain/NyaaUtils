package cat.nyaa.nyaautils.mailbox;

import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.LanguageRepository;
import cat.nyaa.nyaacore.utils.InventoryUtils;
import cat.nyaa.nyaacore.utils.VaultUtils;
import cat.nyaa.nyaautils.NyaaUtils;
import me.crafter.mc.lockettepro.LocketteProAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MailboxCommands extends CommandReceiver {
    private final NyaaUtils plugin;

    public MailboxCommands(Object plugin, LanguageRepository i18n) {
        super((NyaaUtils) plugin, i18n);
        this.plugin = (NyaaUtils) plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "mailbox";
    }

    @SubCommand(value = "create", permission = "nu.mailbox")
    public void createMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        String tmp = args.top();
        if (tmp != null) {
            if (sender.hasPermission("nu.mailadmin")) {
                createMailbox(p, args.nextOfflinePlayer());
            } else {
                msg(sender, "user.mailbox.permission_required");
            }
            return;
        }
        if (plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId()) != null) {
            msg(p, "user.mailbox.already_set");
            return;
        }
        plugin.mailboxListener.registerRightClickCallback(p, 100,
                (Location clickedBlock) -> {
                    Block b = clickedBlock.getBlock();
                    if (b.getState() instanceof Chest) {
                        plugin.cfg.mailbox.updateLocationMapping(p.getUniqueId(), b.getLocation());
                        msg(p, "user.mailbox.set_success");
                        return;
                    }
                    msg(p, "user.mailbox.set_fail");
                });
        msg(p, "user.mailbox.now_right_click");
    }

    public void createMailbox(Player admin, OfflinePlayer player) {
        UUID id = player.getUniqueId();
        if (plugin.cfg.mailbox.getMailboxLocation(id) != null) {
            msg(admin, "user.mailbox.admin.already_set");
            return;
        }
        plugin.mailboxListener.registerRightClickCallback(admin, 100,
                (Location clickedBlock) -> {
                    Block b = clickedBlock.getBlock();
                    if (b.getState() instanceof Chest) {
                        plugin.cfg.mailbox.updateNameMapping(id, player.getName());
                        plugin.cfg.mailbox.updateLocationMapping(id, b.getLocation());
                        msg(admin, "user.mailbox.admin.success_set");
                        if (player.isOnline()) {
                            Player tmp = plugin.getServer().getPlayer(id);
                            if (tmp != null) {
                                msg(tmp, "user.mailbox.admin.player_hint_set");
                            }
                        }
                        return;
                    }
                    msg(admin, "user.mailbox.admin.fail_set");
                });
        msg(admin, "user.mailbox.admin.right_click_set", player);
    }

    @SubCommand(value = "remove", permission = "nu.mailbox")
    public void removeMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        String tmp = args.top();
        if (tmp != null) {
            if (sender.hasPermission("nu.mailadmin")) {
                removeMailbox(p, args.nextOfflinePlayer());
            } else {
                msg(sender, "user.mailbox.permission_required");
            }
            return;
        }
        if (plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId()) == null) {
            msg(p, "user.mailbox.havent_set_self");
            return;
        }
        plugin.cfg.mailbox.updateLocationMapping(p.getUniqueId(), null);
        msg(p, "user.mailbox.remove_success");
    }

    public void removeMailbox(Player admin, OfflinePlayer player) {
        if (plugin.cfg.mailbox.getMailboxLocation(player.getUniqueId()) == null) {
            msg(admin, "user.mailbox.admin.no_mailbox");
        } else {
            UUID id = player.getUniqueId();
            plugin.cfg.mailbox.updateLocationMapping(id, null);
            msg(admin, "user.mailbox.admin.success_remove");
            if (player.isOnline()) {
                msg((Player) player, "user.mailbox.admin.player_hint_removed");
            }
        }
    }

    @SubCommand(value = "info", permission = "nu.mailbox")
    public void infoMailbox(CommandSender sender, Arguments args) {
        String tmp = args.top();
        if (tmp != null) {
            if (sender.hasPermission("nu.mailadmin")) {
                infoMailbox(sender, args.nextOfflinePlayer());
            } else {
                msg(sender, "user.mailbox.permission_required");
            }
            return;
        }
        Player p = asPlayer(sender);

        Location loc = plugin.cfg.mailbox.getMailboxLocation(p.getUniqueId());
        if (loc == null) {
            msg(p, "user.mailbox.havent_set_self");
        } else {

            msg(p, "user.mailbox.info.location", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            msg(p, "user.mailbox.info.hand_price", (float) plugin.cfg.mailHandFee);
            msg(p, "user.mailbox.info.chest_price", (float) plugin.cfg.mailChestFee);
            msg(p, "user.mailbox.info.send_cooldown", ((double) plugin.cfg.mailCooldown) / 20D);
            //msg(p, "user.mailbox.info.send_timeout", ((double) plugin.cfg.mailTimeout) / 20D);
        }
    }

    public void infoMailbox(CommandSender admin, OfflinePlayer player) {
        Location loc = plugin.cfg.mailbox.getMailboxLocation(player.getUniqueId());
        if (loc != null) {
            msg(admin, "user.mailbox.admin.info", player.getName(), player.getUniqueId().toString(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } else {
            msg(admin, "user.mailbox.admin.no_mailbox");
        }
    }

    @SubCommand(value = "send", permission = "nu.mailbox")
    public void sendMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        ItemStack stack = getItemInHand(sender);
        if (args.top() == null) {
            msg(sender, "manual.mailbox.send.usage");
            return;
        }

        if (!VaultUtils.enoughMoney(p, plugin.cfg.mailHandFee)) {
            msg(p, "user.mailbox.money_insufficient");
            return;
        }
        OfflinePlayer toPlayer = args.nextOfflinePlayer();
        UUID recipient = toPlayer.getUniqueId();
        Location toLocation = plugin.cfg.mailbox.getMailboxLocation(recipient);

        // Check remote mailbox
        if (recipient != null && toLocation != null) {
            Block b = toLocation.getBlock();
            if (!(b.getState() instanceof InventoryHolder)) {
                plugin.cfg.mailbox.updateLocationMapping(recipient, null);
                toLocation = null;
            }
        }

        if (recipient == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer.getName());
            return;
        } else if (toLocation == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer.getName());
            if (toPlayer.isOnline()) {
                msg((Player) toPlayer, "user.mailbox.create_mailbox_hint", sender.getName());
            }
            return;
        }

        Player recp = null;
        if (toPlayer.isOnline()) recp = (Player) toPlayer;
        Inventory targetInventory = ((InventoryHolder) toLocation.getBlock().getState()).getInventory();
        if (!InventoryUtils.hasEnoughSpace(targetInventory, stack)) {
            msg(sender, "user.mailbox.recipient_no_space");
            if (recp != null) {
                msg(recp, "user.mailbox.mailbox_no_space", sender.getName());
            }
        } else {
            if (plugin.systemBalance != null) {
                plugin.systemBalance.deposit(plugin.cfg.mailHandFee, plugin);
            }
            InventoryUtils.addItem(targetInventory, stack);
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            msg(sender, "user.mailbox.mail_sent", toPlayer.getName(), (float) plugin.cfg.mailHandFee);
            if (recp != null) {
                msg(recp, "user.mailbox.mail_received", sender.getName());
            }
            VaultUtils.withdraw(p, plugin.cfg.mailHandFee);
        }
    }

    @SubCommand(value = "sendchest", permission = "nu.mailbox")
    public void sendBoxMailbox(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        if (args.top() == null) {
            msg(sender, "manual.mailbox.sendchest.usage");
            return;
        }

        if (!VaultUtils.enoughMoney(p, plugin.cfg.mailChestFee)) {
            msg(p, "user.mailbox.money_insufficient");
            return;
        }
        OfflinePlayer toPlayer = args.nextOfflinePlayer();
        UUID recipient = toPlayer.getUniqueId();
        Location toLocation = plugin.cfg.mailbox.getMailboxLocation(recipient);

        // Check remote mailbox
        if (recipient != null && toLocation != null) {
            Block b = toLocation.getBlock();
            if (!(b.getState() instanceof InventoryHolder)) {
                plugin.cfg.mailbox.updateLocationMapping(recipient, null);
                toLocation = null;
            }
        }

        if (recipient == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer.getName());
            return;
        } else if (toLocation == null) {
            msg(sender, "user.mailbox.player_no_mailbox", toPlayer.getName());
            if (toPlayer.isOnline()) {
                msg((Player) toPlayer, "user.mailbox.create_mailbox_hint", sender.getName());
            }
            return;
        }

        final Location toLocationFinal = toLocation;
        Player recp = null;
        if (toPlayer.isOnline()) recp = (Player) toPlayer;
        final Player recpFinal = recp;

        plugin.mailboxListener.registerRightClickCallback(p, 100,
                (Location boxLocation) -> {
                    if (!VaultUtils.enoughMoney(p, plugin.cfg.mailChestFee)) {
                        msg(p, "user.mailbox.money_insufficient");
                        return;
                    }
                    Block b = boxLocation.getBlock();
                    if (plugin.getServer().getPluginManager().getPlugin("LockettePro") != null) {
                        if (LocketteProAPI.isLocked(b) && !LocketteProAPI.isUser(b, p)) {
                            msg(p, "user.mailbox.chest_protected");
                            return;
                        }
                    }

                    Inventory fromInventory = ((InventoryHolder) b.getState()).getInventory();
                    Inventory toInventory = ((InventoryHolder) toLocationFinal.getBlock().getState()).getInventory();
                    ItemStack[] fromBefore = fromInventory.getStorageContents();
                    ItemStack[] fromAfter = new ItemStack[fromBefore.length];
                    fromInventory.setStorageContents(fromAfter);
                    ItemStack[] to = toInventory.getStorageContents();
                    int nextSlot = 0;
                    boolean itemMoved = false;
                    for (int i = 0; i < fromBefore.length; i++) {
                        if (fromBefore[i] != null && fromBefore[i].getType() != Material.AIR) {
                            while (nextSlot < to.length && to[nextSlot] != null && to[nextSlot].getType() != Material.AIR) {
                                nextSlot++;
                            }
                            if (nextSlot >= to.length) {
                                msg(sender, "user.mailbox.recipient_no_space");
                                if (recpFinal != null) {
                                    msg(recpFinal, "user.mailbox.mailbox_no_space", sender.getName());
                                }
                                fromInventory.setStorageContents(fromBefore);
                                return;
                            }
                            to[nextSlot] = fromBefore[i].clone();
                            itemMoved = true;
                            nextSlot++;
                        }
                    }
                    if (itemMoved) {
                        if (plugin.systemBalance != null) {
                            plugin.systemBalance.deposit(plugin.cfg.mailChestFee, plugin);
                        }
                        toInventory.setStorageContents(to);
                        msg(sender, "user.mailbox.mail_sent", toPlayer.getName(), (float) plugin.cfg.mailChestFee);
                        if (recpFinal != null) {
                            msg(recpFinal, "user.mailbox.mail_received", sender.getName());
                        }
                        VaultUtils.withdraw(p, plugin.cfg.mailChestFee);
                    } else {
                        msg(sender, "user.mailbox.mail_sent_nothing");
                    }
                });
        msg(p, "user.mailbox.now_right_click_send", toPlayer.getName());
    }
}
