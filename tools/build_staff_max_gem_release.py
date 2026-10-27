"""Build the scoped Max Gem addition over the latest delivered Gem/worldgen releases."""
import build_staff_gem_release as release

release.OUT=release.ROOT/'output/staff-max-gems-20261026'
release.MODULES={
    'advance-magic':('1.31.1-max-gems','advance-magic',release.MODULES['advance-magic'][2]),
    'evergarden':('3.0.0-e2.143-max-gems','evergarden',('com/example/voidscape/world/WhaleTreasure',)),
}
release.GEM_VALIDATION='paper-gems'
release.REGRESSIONS=[]

if __name__=='__main__':release.main()
