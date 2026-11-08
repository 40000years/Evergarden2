"""Build and promote the tiered Vault quality/Limit Break release over the delivered e2.172 baseline."""
from pathlib import Path
import build_vault_tandem_loot_release as release

release.OUT = Path(__file__).resolve().parents[1] / 'output/vault-progression-20261030'
release.VERSION = '3.0.0-e2.173-vault-progression'
release.FAMILIES = tuple('com/example/voidscape/' + name for name in (
    'item/RelicService', 'item/VaultLootTable', 'item/VaultEnchantRewards', 'item/VaultProgression',
    'gui/AdminTestGui', 'world/WhaleTreasure', 'enchant/EnchantApplyListener', 'gui/FloodgateUpgradeForm'))

if __name__ == '__main__':
    release.main()
