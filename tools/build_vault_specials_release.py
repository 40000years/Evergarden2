"""Build the Vault rarity/feedback adjustment over the delivered e2.173 baseline."""
from pathlib import Path
import build_vault_tandem_loot_release as release

release.OUT = Path(__file__).resolve().parents[1] / 'output/vault-specials-20261030'
release.VERSION = '3.0.0-e2.174-vault-specials'
release.FAMILIES = tuple('com/example/voidscape/' + name for name in (
    'item/VaultLootTable', 'gui/AdminTestGui', 'item/RelicService'))

if __name__ == '__main__':
    release.main()
