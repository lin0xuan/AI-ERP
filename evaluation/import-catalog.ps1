# 从公开商品目录下载数据，生成订单和库存，写入本机 qihang-erp。
# 商品来自 DummyJSON 与 Fake Store API。订单、库存和会员是按这些商品在本地生成的，不是别人的真实订单。
param(
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [int]$OrderCount = 2500
)

$ErrorActionPreference = "Stop"
if (-not $MysqlPassword) { throw "请设置环境变量 MYSQL_PASSWORD" }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $root "data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

function Get-AllDummyProducts {
    $items = @()
    $skip = 0
    do {
        $page = Invoke-RestMethod -Uri "https://dummyjson.com/products?limit=30&skip=$skip" -TimeoutSec 30
        $items += @($page.products)
        $skip += 30
    } while ($items.Count -lt [int]$page.total)
    return $items
}

$dummyPath = Join-Path $dataDir "dummyjson-products.json"
$fakePath = Join-Path $dataDir "fakestore-products.json"
if ((Test-Path $dummyPath) -and (Get-Item $dummyPath).Length -gt 100000) {
    Write-Host "使用已下载的 DummyJSON 目录"
    $dummy = @(Get-Content $dummyPath -Raw -Encoding utf8 | ConvertFrom-Json)
} else {
    Write-Host "下载 DummyJSON 商品目录"
    $dummy = @(Get-AllDummyProducts)
    $dummy | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 $dummyPath
}
Write-Host "DummyJSON $($dummy.Count) 条"

if ((Test-Path $fakePath) -and (Get-Item $fakePath).Length -gt 1000) {
    Write-Host "使用已下载的 Fake Store 目录"
    $fake = @(Get-Content $fakePath -Raw -Encoding utf8 | ConvertFrom-Json)
} else {
    Write-Host "下载 Fake Store API 商品目录"
    $fakeRaw = Invoke-RestMethod -Uri "https://fakestoreapi.com/products" -TimeoutSec 30
    $fake = @($fakeRaw)
    if ($fake.Count -eq 1 -and $fake[0] -is [System.Array]) { $fake = @($fake[0]) }
    $fake | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 $fakePath
}
Write-Host "Fake Store $($fake.Count) 条"

function Sql-Quote([string]$value) {
    if ($null -eq $value) { return "NULL" }
    return "'" + ($value -replace "\\", "\\\\" -replace "'", "''") + "'"
}
function Clip([string]$value, [int]$length) {
    if ($null -eq $value) { return "" }
    if ($value.Length -le $length) { return $value }
    return $value.Substring(0, $length)
}

$catalog = @()
$n = 1
foreach ($p in $dummy) {
    $catalog += [pscustomobject]@{
        Code = ("DJ{0:D4}" -f $n)
        Name = Clip $p.title 50
        Image = Clip ($(if ($p.thumbnail) { $p.thumbnail } else { $p.images[0] })) 250
        Price = [double]$p.price
        Stock = [int]$p.stock
        Category = Clip $p.category 15
        Brand = Clip $p.brand 15
        Source = "dummyjson"
    }
    $n++
}
$n = 1
foreach ($p in $fake) {
    $catalog += [pscustomobject]@{
        Code = ("FS{0:D4}" -f $n)
        Name = Clip $p.title 50
        Image = Clip $p.image 250
        Price = [double]$p.price
        Stock = 40
        Category = Clip $p.category 15
        Brand = "FakeStore"
        Source = "fakestoreapi"
    }
    $n++
}

$mysql = "C:\00x_app\environment\mysql-8.4.11\mysql-8.4.11-winx64\bin\mysql.exe"
$sqlFile = Join-Path $dataDir "import.sql"
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("SET NAMES utf8mb4;")
[void]$sb.AppendLine("SET FOREIGN_KEY_CHECKS=0;")
[void]$sb.AppendLine("DELETE i FROM o_order_item i INNER JOIN o_order o ON o.id=i.order_id WHERE o.order_num LIKE 'EVAL-%';")
[void]$sb.AppendLine("DELETE FROM o_order WHERE order_num LIKE 'EVAL-%';")
[void]$sb.AppendLine("DELETE FROM o_goods_inventory WHERE sku_code LIKE 'DJ%' OR sku_code LIKE 'FS%';")
[void]$sb.AppendLine("DELETE FROM o_goods_sku WHERE sku_code LIKE 'DJ%' OR sku_code LIKE 'FS%';")
[void]$sb.AppendLine("DELETE FROM o_goods WHERE goods_num LIKE 'DJ%' OR goods_num LIKE 'FS%';")
[void]$sb.AppendLine("DELETE FROM oms_shop_member WHERE phone LIKE '1380000%';")
[void]$sb.AppendLine("DELETE FROM o_shop WHERE name='测评线下店';")
[void]$sb.AppendLine("DELETE FROM erp_warehouse WHERE warehouse_no='EVAL-WH';")
[void]$sb.AppendLine("INSERT INTO erp_warehouse (warehouse_type, warehouse_no, warehouse_name, status, province, city, address, contacts, phone, create_by, create_time, merchant_id, remark, warehouse_source, shop_id) VALUES ('LOCAL','EVAL-WH','测评仓','1','上海','上海','浦东新区测评路1号','测评员','13800000000','eval',NOW(),0,'evaluation',1,0);")
[void]$sb.AppendLine("SET @wh := LAST_INSERT_ID();")
[void]$sb.AppendLine("INSERT INTO o_shop (name, type, sort, status, remark, region_id, merchant_id, create_time, create_by, api_status, allow_inventory_share) VALUES ('测评线下店',999,1,1,'evaluation',1,0,NOW(),'eval',0,0);")
[void]$sb.AppendLine("SET @shop := LAST_INSERT_ID();")

$goodsRows = New-Object System.Collections.Generic.List[string]
$skuRows = New-Object System.Collections.Generic.List[string]
$invRows = New-Object System.Collections.Generic.List[string]
foreach ($item in $catalog) {
    $low = 5
    $available = $item.Stock
    if ($item.Code.EndsWith("001") -or $item.Code.EndsWith("002") -or $item.Code.EndsWith("003")) {
        $low = 20
        if ($available -ge 20) { $available = 8 }
    }
    $goodsRows.Add(("({0},{1},{2},{3},{4},1,0,0,0,{5},10,0,'eval',NOW(),0,0,0)" -f `
        (Sql-Quote $item.Name), (Sql-Quote $item.Image), (Sql-Quote $item.Code), (Sql-Quote $item.Category), `
        $item.Price, $low))
}
[void]$sb.AppendLine("INSERT INTO o_goods (name, image, goods_num, attr2, retail_price, status, disable, supplier_id, brand_id, low_qty, ship_type, merchant_id, create_by, create_time, price_type, inventory_mode, shop_id) VALUES")
[void]$sb.AppendLine(($goodsRows -join ",`n") + ";")

$i = 0
foreach ($item in $catalog) {
    $i++
    $low = 5
    $available = [Math]::Max(0, $item.Stock)
    if ($item.Code.EndsWith("001") -or $item.Code.EndsWith("002") -or $item.Code.EndsWith("003")) {
        $low = 20
        if ($available -ge 20) { $available = 8 }
    }
    $skuRows.Add(("((SELECT id FROM o_goods WHERE goods_num={0}),{1},{0},{2},{0},{3},{3},1,{4},10,0,0,0,0,0,0,0,0,0,0)" -f `
        (Sql-Quote $item.Code), (Sql-Quote $item.Name), (Sql-Quote $item.Category), $item.Price, $low))
    $invRows.Add(("((SELECT id FROM o_goods WHERE goods_num={0}),{0},(SELECT id FROM o_goods_sku WHERE sku_code={0}),{0},{1},{2},{1},{3},0,{3},@wh,0,0,1,0,'eval')" -f `
        (Sql-Quote $item.Code), (Sql-Quote $item.Name), (Sql-Quote $item.Image), $available))
}
[void]$sb.AppendLine("INSERT INTO o_goods_sku (goods_id, goods_name, goods_num, sku_name, sku_code, pur_price, retail_price, status, low_qty, ship_type, merchant_id, length, height, width, weight, price_type, weight1, weight2, weight3, inventory_mode) VALUES")
[void]$sb.AppendLine(($skuRows -join ",`n") + ";")
[void]$sb.AppendLine("INSERT INTO o_goods_inventory (goods_id, goods_num, sku_id, sku_code, goods_name, goods_img, sku_name, quantity, locked_quantity, available_quantity, warehouse_id, merchant_id, shop_id, stock_status, is_delete, create_by) VALUES")
[void]$sb.AppendLine(($invRows -join ",`n") + ";")

$memberRows = New-Object System.Collections.Generic.List[string]
for ($m = 1; $m -le 30; $m++) {
    $phone = "1380000{0:D4}" -f $m
    $memberRows.Add(("(0,@shop,-1,{0},{1},{2},1,NOW())" -f (Sql-Quote $phone), (Sql-Quote $phone), (Sql-Quote ("测评会员{0}" -f $m))))
}
[void]$sb.AppendLine("INSERT INTO oms_shop_member (merchant_id, shop_id, shop_type, platform_account, phone, name, status, create_on) VALUES")
[void]$sb.AppendLine(($memberRows -join ",`n") + ";")

$cities = @("上海","杭州","南京","苏州","宁波","合肥","武汉","成都","西安","青岛")
$rng = [System.Random]::new(20261117)
$orderRows = New-Object System.Collections.Generic.List[string]
$itemRows = New-Object System.Collections.Generic.List[string]
for ($o = 1; $o -le $OrderCount; $o++) {
    $product = $catalog[$rng.Next($catalog.Count)]
    $qty = $rng.Next(1, 4)
    $amount = [Math]::Round($product.Price * $qty, 2)
    $roll = $rng.Next(100)
    $status = 3
    $ship = 2
    $hoursAgo = $rng.Next(1, 24 * 14)
    if ($roll -lt 35) { $status = 1; $ship = 0; if ($o % 7 -eq 0) { $hoursAgo = $rng.Next(5, 30) } }
    elseif ($roll -lt 60) { $status = 2; $ship = 2 }
    elseif ($roll -lt 90) { $status = 3; $ship = 2 }
    else { $status = 11; $ship = 0 }
    $city = $cities[$rng.Next($cities.Count)]
    $orderNum = "EVAL-{0:D6}" -f $o
    $when = "DATE_SUB(NOW(), INTERVAL $hoursAgo HOUR)"
    $orderRows.Add(("('{0}',0,999,@shop,0,{1},{2},{2},{2},'测评收件人{3}','1380000{4:D4}','{5}市测评路{3}号','{5}','{5}','城区',{6},{6},{7},0,{6})" -f `
        $orderNum, $status, $amount, $o, ($o % 30 + 1), $city, $when, $ship))
    $itemRows.Add(("((SELECT id FROM o_order WHERE order_num='{0}'),'{0}','{0}-1','{1}',(SELECT id FROM o_goods WHERE goods_num='{1}'),(SELECT id FROM o_goods_sku WHERE sku_code='{1}'),{2},{3},'{1}','{1}',{4},{5},{6},{7},0,10,0,0,999,@shop,{8})" -f `
        $orderNum, $product.Code, (Sql-Quote $product.Name), (Sql-Quote $product.Image), $product.Price, $amount, $qty, $ship, $when))
    if ($orderRows.Count -ge 400) {
        [void]$sb.AppendLine("INSERT INTO o_order (order_num, order_mode, shop_type, shop_id, merchant_id, order_status, goods_amount, amount, payment, receiver_name, receiver_mobile, address, province, city, town, order_time, create_time, ship_status, dist_status, update_time) VALUES")
        [void]$sb.AppendLine(($orderRows -join ",`n") + ";")
        [void]$sb.AppendLine("INSERT INTO o_order_item (order_id, order_num, sub_order_num, sku_id, goods_id, goods_sku_id, goods_title, goods_img, goods_num, sku_num, goods_price, item_amount, quantity, ship_status, refund_status, ship_type, shipper_id, merchant_id, shop_type, shop_id, order_time) VALUES")
        [void]$sb.AppendLine(($itemRows -join ",`n") + ";")
        $orderRows.Clear(); $itemRows.Clear()
    }
}
if ($orderRows.Count -gt 0) {
    [void]$sb.AppendLine("INSERT INTO o_order (order_num, order_mode, shop_type, shop_id, merchant_id, order_status, goods_amount, amount, payment, receiver_name, receiver_mobile, address, province, city, town, order_time, create_time, ship_status, dist_status, update_time) VALUES")
    [void]$sb.AppendLine(($orderRows -join ",`n") + ";")
    [void]$sb.AppendLine("INSERT INTO o_order_item (order_id, order_num, sub_order_num, sku_id, goods_id, goods_sku_id, goods_title, goods_img, goods_num, sku_num, goods_price, item_amount, quantity, ship_status, refund_status, ship_type, shipper_id, merchant_id, shop_type, shop_id, order_time) VALUES")
    [void]$sb.AppendLine(($itemRows -join ",`n") + ";")
}
[void]$sb.AppendLine("SET FOREIGN_KEY_CHECKS=1;")
[System.IO.File]::WriteAllText($sqlFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding $false))
Write-Host "SQL 写入 $sqlFile 大小 $((Get-Item $sqlFile).Length)"

cmd /c "`"$mysql`" --user=root --password=$MysqlPassword --host=127.0.0.1 --port=3306 --protocol=TCP --default-character-set=utf8mb4 qihang-erp < `"$sqlFile`""
if ($LASTEXITCODE -ne 0) { throw "导入失败，退出码 $LASTEXITCODE" }
& $mysql --user=root --password=$MysqlPassword --host=127.0.0.1 --port=3306 --protocol=TCP --default-character-set=utf8mb4 qihang-erp -e "SELECT 'goods' t, COUNT(*) c FROM o_goods WHERE goods_num LIKE 'DJ%' OR goods_num LIKE 'FS%' UNION ALL SELECT 'sku', COUNT(*) FROM o_goods_sku WHERE sku_code LIKE 'DJ%' OR sku_code LIKE 'FS%' UNION ALL SELECT 'inventory', COUNT(*) FROM o_goods_inventory WHERE sku_code LIKE 'DJ%' OR sku_code LIKE 'FS%' UNION ALL SELECT 'orders', COUNT(*) FROM o_order WHERE order_num LIKE 'EVAL-%' UNION ALL SELECT 'items', COUNT(*) FROM o_order_item WHERE order_num LIKE 'EVAL-%' UNION ALL SELECT 'members', COUNT(*) FROM oms_shop_member WHERE phone LIKE '1380000%';"
