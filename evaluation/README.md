# 测评数据与压测

这个目录用来重复导入公开商品目录、生成可查询的订单，并对本机接口做短时压测。

## 数据从哪来

商品目录来自两个公开接口，原始响应保存在 `data/`：

- [DummyJSON Products](https://dummyjson.com/products)：194 条
- [Fake Store API](https://fakestoreapi.com/products)：20 条

订单、库存数量和会员是按这些商品在本地生成的，订单号以 `EVAL-` 开头。没有抓取任何真实店铺的订单或客户资料。

导入结果：商品 214、SKU 214、库存 214、订单 2500、订单明细 2500、会员 30。另有测评仓 `EVAL-WH` 和店铺「测评线下店」。部分 SKU 的可用库存低于预警线，用来触发库存不足扫描。

## 重新导入

先设置数据库密码，再在项目根目录执行：

```powershell
$env:MYSQL_PASSWORD = "your-mysql-password"
.\evaluation\import-catalog.ps1 -OrderCount 2500
```

脚本会先删掉上一轮 `DJ` / `FS` / `EVAL-` 数据，再写入。已下载的 JSON 会复用，避免每次都请求外网。

## 压测

接口需要已经启动，默认地址 `http://127.0.0.1:8088`。

```powershell
.\evaluation\load-test.ps1 -Workers 20 -Seconds 15
```

2026-11-17 的一次结果在 `results/load-test-20261117-141320.json`。20 个并发、持续 15 秒，每个列表接口 754 次请求，失败 0。

| 接口 | 平均 | P50 | P95 | 最大 |
| --- | ---: | ---: | ---: | ---: |
| 商品列表 | 103 ms | 76 ms | 265 ms | 587 ms |
| SKU 搜索 | 26 ms | 17 ms | 84 ms | 185 ms |
| 订单列表 | 78 ms | 63 ms | 184 ms | 619 ms |
| 店铺列表 | 23 ms | 14 ms | 68 ms | 317 ms |
| 会员列表 | 139 ms | 117 ms | 341 ms | 657 ms |
| SKU 列表 | 24 ms | 15 ms | 73 ms | 261 ms |
| 销售分析（单次） | 46 ms | 46 ms | 46 ms | 46 ms |

会员列表是这次最慢的读接口。销售分析在没有默认模型时只做数据库汇总，所以很快。平台拉取没有放进压测，因为那需要各平台的真实凭证。
