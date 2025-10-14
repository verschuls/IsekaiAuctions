# IsekaiAuctions

A performance-focused auction plugin for Minecraft servers, forked from DeluxeAuctions with significant improvements to stability, database handling, and multiserver support.

## Features

- **Modern Database Layer**: HikariCP connection pooling with support for H2, MySQL, MariaDB, and PostgreSQL
- **Native Redis Support**: Built-in multiserver synchronization without external addons
- **LiteCommands**: Clean command system with built-in cooldowns and permission management
- **Multiple Economy Support**: Works with Vault, CoinsEngine, PlayerPoints, UltraEconomy, Lands, and custom item-based economy
- **Two Auction Types**:
  - BIN (Buy It Now) - instant purchase
  - Normal Auctions - bidding system with auto-time extension
- **Category System**: Organize items into customizable categories
- **Player Statistics**: Track auction performance (won/lost bids, earnings, fees)
- **Discord Webhooks**: Send auction notifications to Discord
- **Anti-Lag Features**: TPS and ping checks to prevent dupe exploits
- **Folia Support**: Works on both Paper and Folia servers

## Requirements

- Java 21+
- Minecraft 1.16.5+ (targeting 1.19+)
- An economy plugin is **strongly recommended** (Vault, CoinsEngine, etc.)

## Installation

1. Download the latest JAR from [Releases](https://github.com/Verschuls/IsekaiAuctions/releases) (or build from source)
2. Drop into your `plugins/` folder
3. Restart server
4. Configure `config.yml` to your needs

## Database Setup

### Local (Default)
By default, the plugin uses **H2** - no configuration needed. Data is stored in `plugins/IsekaiAuctions/database.*`.

### Remote Database
For better performance or multiserver setups, configure in `config.yml`:

```yaml
storage_method: mariadb  # Options: h2, mysql, mariadb, postgresql

database:
  address: "localhost:3306"
  database: "isekaiauctions"
  username: "username"
  password: "password"
```

**Database drivers are automatically downloaded** - no manual installation required.

## Multiserver Setup (Redis)

Enable Redis in `config.yml` for real-time auction sync across servers:

```yaml
redis:
  enabled: true
  channel: "isekaiauctions"
  host: "localhost"
  port: 6379
  password: ""  # Leave empty if no auth
```

All auction events (create/bid/purchase) are instantly synchronized between connected servers.

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/auction` | `isekaiauctions.commands.*` | Main command |
| `/ah` | `isekaiauctions.commands.auctions` | Open auction house |
| `/ah menu` | `isekaiauctions.commands.menu` | Open main menu |
| `/ah sell <price> [duration] [type]` | `isekaiauctions.commands.sell` | Create auction |
| `/ah view <player/uuid>` | `isekaiauctions.commands.view` | View player's auctions |
| `/ah bids` | `isekaiauctions.commands.bids` | View your bids |
| `/ah manage` | `isekaiauctions.commands.manage` | Manage your auctions |
| `/ahadmin reload` | `isekaiauctions.commands.admin.reload` | Reload config |
| `/ahadmin lock` | `isekaiauctions.commands.admin.lock` | Lock/unlock auction house |
| `/ahadmin cancel <uuid>` | `isekaiauctions.commands.admin.cancel` | Cancel any auction |

## Configuration

Key settings in `config.yml`:

```yaml
settings:
  default_economy: vault_economy
  default_type: "BIN"          # BIN or NORMAL
  default_price: 1000
  default_duration: 21600      # 6 hours in seconds
  purge_auctions: 60           # Delete auctions older than X days
  bid_formula: "%highest_bid% + %highest_bid% / 10"  # Min bid increment

# Auction fees
bin_auction:
  fee: true
  price_fees:
    0: 1        # 1% for 0-500k
    500000: 2.5 # 2.5% for 500k-1M
    1000000: 5  # 5% for 1M+
```

## API Usage

```java
// Get player's auctions
Collection<Auction> auctions = AuctionCache.getPlayerAuctions(uuid);

// Listen for auction events
@EventHandler
public void onAuctionCreate(AuctionCreateEvent event) {
    Player player = event.getPlayer();
    Auction auction = event.getAuction();
    // ...
}

// Open auction GUI
AuctionHook.openMainMenu(player);
```

**Available Events:**
- `AuctionCreateEvent` - Auction created
- `AuctionPurchaseEvent` - Item purchased
- `PlayerBidEvent` - Player placed bid
- `AuctionCancelEvent` - Auction cancelled
- `AuctionCollectEvent` - Player collected item/money

## Building from Source

```bash
git clone https://github.com/Verschuls/IsekaiAuctions.git
cd IsekaiAuctions
./gradlew build
```

Output: `build/libs/IsekaiAuctions-3.1.jar`

## Support

- **Discord**: https://discord.gg/XbpJHXfMXu (community support, responses may be slow)
- **Issues**: Report bugs on [GitHub Issues](https://github.com/Verschuls/IsekaiAuctions/issues)

**Note**: This is an open-source project maintained in spare time. Support is not guaranteed to be immediate.

## Known Issues

- Database layer is still being refined - some edge cases may cause issues
- Full migration to JDBI3 is planned for future releases

## Contributing

Contributions are not currently being accepted as the project structure is being reorganized. Check back later for contribution guidelines.

## Credits

- **Original Plugin**: DeluxeAuctions
- **Original Author**: SedatTR
- **Maintainer**: Verschuls
- **License**: GNU General Public License v3.0 (GPL-3.0)

This project is a fork aimed at improving performance, stability, and modernizing the codebase of the original DeluxeAuctions plugin.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

**Website**: https://verschuls.xyz