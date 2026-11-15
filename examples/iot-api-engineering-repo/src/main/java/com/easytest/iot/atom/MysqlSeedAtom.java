package com.easytest.iot.atom;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.JdbcAtom;
import com.easytest.iot.config.IotConfig;

/** ATOM: per-application MySQL connection and data seeding/verification. */
public class MysqlSeedAtom {

    @EasyContract(id = "iot.atom.mysql.seedHome", name = "MySQL 造家庭数据", layer = ContractLayer.ATOM,
            protocols = {"SQL"}, description = "通过 DB 操作创建家庭/用户造数",
            inputs = {"homeId", "ownerId"}, outputs = {"rows"}, tags = {"iot", "mysql", "seed"})
    public ContractResult seedHome(ContractContext context) {
        String homeId = context.requireString("homeId");
        String ownerId = context.requireString("ownerId");
        int rows = JdbcAtom.update(IotConfig.mysqlUrl(), IotConfig.mysqlUser(), IotConfig.mysqlPassword(),
                "INSERT INTO home(id, owner_id, name) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id)",
                homeId, ownerId, "easy-home");
        context.output("rows", rows);
        return ContractResult.ok("DB 造数完成");
    }

    @EasyContract(id = "iot.atom.mysql.queryHome", name = "MySQL 查询家庭", layer = ContractLayer.ATOM,
            protocols = {"SQL"}, description = "查询家庭服务落库结果",
            inputs = {"homeId"}, outputs = {"row"}, tags = {"iot", "mysql"})
    public ContractResult queryHome(ContractContext context) {
        String homeId = context.requireString("homeId");
        var row = JdbcAtom.queryOne(IotConfig.mysqlUrl(), IotConfig.mysqlUser(), IotConfig.mysqlPassword(),
                "SELECT id, owner_id, name FROM home WHERE id = ?", homeId);
        context.output("row", row);
        return row == null ? ContractResult.fail("家庭不存在: " + homeId) : ContractResult.ok("家庭存在");
    }
}
