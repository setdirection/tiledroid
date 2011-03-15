var Canvas = require("canvas"),
    fs = require("fs");

var argv = require("optimist")
        .usage("Usage: sourceImage tileSize destinationDir")
        .demand(3)
        .argv;

var sourceImagePath = argv._[0],
    tileSize = parseInt(argv._[1], 10),
    destinationDir = argv._[2];

function getImageMetrics(width, height, tileSize) {
    var xTileCount = Math.ceil(width / tileSize),
        yTileCount = Math.ceil(height / tileSize),

        maxTileCount = Math.max(xTileCount, yTileCount),
        zoomLevels = Math.ceil(Math.log(maxTileCount) / Math.LN2);

    return {
        zoomLevels: zoomLevels,
        maxTileCount: maxTileCount,
        xTileCount: xTileCount,
        yTileCount: yTileCount,
        worldSize: {
            width: width,
            height: height
        },
        padWidth: xTileCount*tileSize - width,
        padHeight: yTileCount*tileSize - height
    };
}

function generateTilesForLevel(level) {
    var levelDir = destinationDir + "/" + level,
        previewLevelScale = Math.pow(2, tileMetrics.zoomLevels-level),
        scaledWidth = tileMetrics.worldSize.width / previewLevelScale,
        scaledHeight = tileMetrics.worldSize.height / previewLevelScale,
        columnCount = Math.ceil(scaledWidth/tileSize),
        rowCount = Math.ceil(scaledHeight/tileSize);

    // Output a preview image for debugging
    var canvas = new Canvas(scaledWidth, scaledHeight),
        context = canvas.getContext("2d");

    var realTileSize = img.width / Math.pow(2, level);
    console.log("drawPreview", 0, 0, img.width, img.height, 0, 0, scaledWidth, scaledHeight, columnCount, rowCount);
    context.drawImage(img, 0, 0, img.width, img.height, 0, 0, scaledWidth, scaledHeight);
    var out = fs.createWriteStream(levelDir + "/preview.png"),
        stream = canvas.createPNGStream();
    stream.on('data', function(chunk){
        out.write(chunk);
    });

    var rightTileSize = scaledWidth % tileSize,
        bottomTileSize = scaledHeight % tileSize;

    // Output each of the individual tiles
    for (var x = 0; x < columnCount; x++) {
        var columnDir = levelDir + "/" + x;
        fs.mkdir(columnDir, 0755, (function(x, columnDir) {
            return function(err) {
                if (err && err.code != "EEXIST") {
                    console.error(err);
                    return;
                }

                for (var y = 0; y < rowCount; y++) {
                    var tileSizeX = tileSize,
                        tileSizeY = tileSize;

                    if (x+1 === columnCount) {
                        // We are at the right edge, reduce the tileSize if we don't fill the entire region
                        tileSizeX = rightTileSize;
                    }
                    if (y+1 === rowCount) {
                        // We are at the bottom edge, reduce the tileSize if we won't fill the tile
                        tileSizeY = bottomTileSize;
                    }

                    generateTile(level, x, y, tileSizeX, tileSizeY, columnDir);
                }
            };
        })(x, columnDir));
    }
}
function generateTile(level, x, y, tileSizeX, tileSizeY, rootDir) {
    // Since we can't clip easily on android (yep another retardation there), we have to send out a whole tile
    // even if we only display data on a small portion of it.
    var canvas = new Canvas(tileSize, tileSize),
        previewLevelScale = Math.pow(2, tileMetrics.zoomLevels-level),
        context = canvas.getContext("2d");

    var tileScale = Math.pow(2, tileMetrics.zoomLevels-level),
        realTileSize = tileSize*tileScale,
        sourceX = x*realTileSize,
        sourceY = y*realTileSize,
        sourceWidth = tileSizeX*tileScale,
        sourceHeight = tileSizeY*tileScale;
    console.log(
        "level", level,
        "x", x, "y", y,
        "sourceX", sourceX, "sourceY", sourceY,
        "sourceWidth", sourceWidth, "sourceHeight", sourceHeight,
        "tileSizeX", tileSizeX, "tileSizeY", tileSizeY,
        "realTileSize", realTileSize);

    context.drawImage(
        img,
        sourceX, sourceY,
        sourceWidth,
        sourceHeight,
        0, 0,
        tileSizeX, tileSizeY);
    var out = fs.createWriteStream(rootDir + "/" + y + ".png"),
        stream = canvas.createPNGStream();

    //console.log("fileOut", rootDir + "/" + y + ".png");
    stream.on('data', function(chunk){
        out.write(chunk);
    });
}

var img = new Canvas.Image(),
    tileMetrics;
img.onload = function() {
    var imgWidth = img.width,   //img.naturalWidth,
        imgHeight = img.height; //img.naturalHeight;

    tileMetrics = getImageMetrics(imgWidth, imgHeight, tileSize);
    console.log("width", imgWidth, imgHeight);
    console.log("tileMetrics", tileMetrics);
    console.log("resize", imgWidth + tileMetrics.padWidth, imgHeight + tileMetrics.padHeight);

    for (var zoomLevel = 0; zoomLevel <= tileMetrics.zoomLevels; zoomLevel++) {
        fs.mkdir(destinationDir + "/" + zoomLevel, 0755, (function(level) {
            return function(err) {
                if (err && err.code != "EEXIST") {
                    console.error(err);
                    return;
                }

                generateTilesForLevel(level);
            };
        })(zoomLevel));
    }
};
img.onerror = function(err) {
    console.error("Failed to load image", sourceImagePath, err.message, err.stack);
};
img.src = sourceImagePath;
