param(
    [Parameter(Mandatory = $true)][string]$SunImage,
    [Parameter(Mandatory = $true)][string]$TimeImage
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'art/cores'

function Save-CoreSprite([string]$source, [string]$name) {
    $image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $source).Path)
    $canvas = [System.Drawing.Bitmap]::new(128, 128, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    try {
        # Production size conversion only; preserve the generated artwork and alpha.
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $destination = [System.Drawing.Rectangle]::new(0, 0, 128, 128)
        $graphics.DrawImage($image, $destination, 0, 0, $image.Width, $image.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $canvas.Save((Join-Path $assetDirectory $name), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $canvas.Dispose()
        $image.Dispose()
    }
}

Save-CoreSprite $SunImage 'core_solar_apocalypse.png'
Save-CoreSprite $TimeImage 'core_chronos_final_hour.png'
