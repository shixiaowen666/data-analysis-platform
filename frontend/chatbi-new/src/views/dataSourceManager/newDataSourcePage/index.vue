<template>
  <div class="ds-form-wrap">
    <div class="ds-form__section-title">基本信息 · 连接配置

      
    </div>
<div class="horizontal-line" style="margin: 8px 0 8px 0;"></div>
    <el-form
      label-position="top"
      size="small"
      class="ds-form"
      autocomplete="off"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="数据源名称" required>
            <el-input
              v-model="dataSource.name"
              placeholder="给数据源起个业务可辨识的名字"
            ></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据库类型" required>
            <el-select
              v-model="dataSource.dbType"
              placeholder="数据库类型"
              @change="selectDatabaseType"
              style="width: 100%"
            >
              <el-option
                v-for="item in databaseTypeList"
                :key="item.name"
                :label="item.name"
                :value="item.name"
              >
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="主机地址/IP" required>
            <el-input
              v-model="dataSource.host"
              placeholder="如10.0.1.100"
            ></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="端口" required>
            <el-input v-model="dataSource.port" placeholder="3306"></el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="数据库/Database" required>
            <el-input
              v-model="dataSource.defaultDb"
              placeholder="MySql 的 database, 如 clinic_ops"
            ></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Schema(选填)">
            <el-input
              v-model="dataSource.schemaName"
              placeholder="PG/GaussDB选填"
            ></el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="用户名" required>
            <el-input
              v-model="dataSource.username"
              placeholder="输入用户名"
            ></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item required>
            <template slot="label"
              >密码
              <span class="ds-form__hint" v-if="dataSourceItem != null"
                >(不修改时不输入)</span
              ></template
            >
            <el-input
              v-model="dataSource.password"
              placeholder="输入密码"
              show-password
              autocomplete="new-password"
            ></el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="JDBC URL(自动生成,仅供确认)">
        <div class="ds-form__jdbc">{{ JDBCSplice }}&nbsp;</div>
      </el-form-item>

      <el-form-item v-if="JDBCTestText != ''">
        <div class="ds-form__test-ok" v-if="testResult">
          <base-icon name="check-circle" :size="14" /> {{ JDBCTestText }}
        </div>

        <div class="ds-form__test-fail" v-else>
          <base-icon name="x-circle" :size="14" /> {{ JDBCTestText }}
        </div>
      </el-form-item>
    </el-form>

    <div class="horizontal-line"></div>

    <div style="display: flex;">
      <el-button class="toolbar-btn" @click="testDataSource()"
        ><base-icon name="zap" :size="14" />&nbsp;测试连接</el-button
      >
      <div style="flex: 1"></div>
      <el-button @click="close()">取消</el-button>
      <el-button type="primary" @click="newDataSource()">
        <div v-if="dataSourceItem == null">创建</div>
        <div v-else>更新</div>
      </el-button>
    </div>
  </div>
</template>

<script>
import {
  getDataSourceDetailAPI,
  newDataSourceAPI,
  testDataSourceAPI,
  editDataSourceAPI,
  getDBTypeAPI,
} from "@/api/dataSourceManager/dataSourceAPI.js";

import JSEncrypt from "jsencrypt";
import { RSA_PUBLIC_KEY } from "@/utils/common";
import toast from "@/utils/toast";

export default {
  name: "newDataSourcePage",
  props: ["dataSourceItem"], //传递过来的行
  components: {},
  data() {
    return {
      JDBCPrefix: "",
      JDBCURL: "",
      JDBCTestText: "",

      testResult: false,

      //数据库类型列表
      databaseTypeList: [],
      //数据源，用于显示和保存
      dataSource: {
        name: "",
        dbType: "",
        host: "",
        port: "",
        defaultDb: "",
        schemaName: "",
        username: "",
        password: "",
      },
    };
  },

  computed: {
    JDBCSplice: function () {
      var host = this.dataSource.host || "";
      var port = this.dataSource.port || "";
      var defaultDb = this.dataSource.defaultDb || "";
      if (host !== "" || port !== "" || defaultDb !== "") {
        return this.JDBCPrefix + host + ":" + port + "/" + defaultDb;
      } else {
        return this.JDBCPrefix;
      }
      //else
      //  return this.dataSource.jdbcUrl
    },
  },

  async mounted() {
    await this.getDBType();
    this.getDataSourceDetail();
  },

  methods: {
    //获取数据源详情
    getDataSourceDetail() {
      if (this.dataSourceItem != null) {
        //const loading = this.loadingScreen()
        getDataSourceDetailAPI(this.dataSourceItem.id)
          .then((response) => {
            if (response.code == 200) {
              this.dataSource = response.data;
              //this.dataSource.password = '******'
              this.$nextTick(() => {
                this.selectDatabaseType(this.dataSource.dbType);
              });
            } else {
              //访问失败
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
            //loading.close()
          });
      }
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },

    detectError() {
      if (!this.dataSource.name?.toString().trim()) {
        toast.error("请选择数据源名称");
        return false;
      }

      if (!this.dataSource.dbType) {
        toast.error("请选择数据库类型");
        return false;
      }

      if (!this.dataSource.host?.toString().trim()) {
        toast.error("请输入主机地址");
        return false;
      }

      if (!this.dataSource.port?.toString().trim()) {
        toast.error("请输入端口");
        return false;
      }

      if (!this.dataSource.defaultDb?.toString().trim()) {
        toast.error("请输入数据库");
        return false;
      }

      if (!this.dataSource.username?.toString().trim()) {
        toast.error("请输入用户名");
        return false;
      }
      return true;
    },

    //测试连接
    testDataSource() {
      //this.getJDBCURL()
      if (!this.detectError()) return;
      this.JDBCTestText = "";
      this.testResult = false;

      let password = null;
      if (this.dataSource.password != null && this.dataSource.password != "") {
        const encryptor = new JSEncrypt();
        encryptor.setPublicKey(RSA_PUBLIC_KEY);
        const rawText = this.dataSource.password + "|" + Date.now();
        const encryptedPassword = encryptor.encrypt(rawText);
        password = encryptedPassword;
      }

      let data = {
        id: this.dataSource.id,
        dbType: this.dataSource.dbType,
        host: this.dataSource.host,
        port: this.dataSource.port,
        defaultDb: this.dataSource.defaultDb,
        schemaName: this.dataSource.schemaName,
        username: this.dataSource.username,
        jdbcUrl: this.dataSource.jdbcUrl,
      };
      if (password != null) {
        data.password = password;
      }

      //const loading = this.loadingScreen()
      testDataSourceAPI(data)
        .then((response) => {
          if (response.code == 200) {
            if (response.data.success) {
              //成功
              this.testResult = true;
              this.JDBCTestText = response.data.message;
            } else {
              this.JDBCTestText = response.data.message;
            }
          } else {
            this.JDBCTestText = response.message;
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close()
        });
    },

    //新建和更新数据源
    newDataSource() {
      if (!this.detectError()) return;
      let dataSource = JSON.parse(JSON.stringify(this.dataSource));

      if (dataSource.password == null || dataSource.password == "") {
        delete dataSource.password;
      } else {
        const encryptor = new JSEncrypt();
        encryptor.setPublicKey(RSA_PUBLIC_KEY);
        const rawText = dataSource.password + "|" + Date.now();
        const encryptedPassword = encryptor.encrypt(rawText);
        dataSource.password = encryptedPassword;
      }

      if (this.dataSourceItem == null) {
        newDataSourceAPI(dataSource)
          .then((response) => {
            if (response.code == 200) {
              //成功
              toast.success("添加成功");
              this.$emit("sure");
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
          });
      } else {
        editDataSourceAPI(this.dataSourceItem.id, dataSource)
          .then((response) => {
            if (response.code == 200) {
              toast.success("修改成功");
              this.$emit("sure");
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
            //loading.close()
          });
      }
    },

    /*getJDBCURL() {
      let data = {
        dbType: "MySQL",
        host: "10.0.1.100",
        port: 3306,
        defaultDb: "clinic_ops",
        schemaName: "",
      };
      getJDBCURLAPI(data).then((response) => {
        if (response.code == 200) {
          this.JDBCURL = response.data;
        } else {
          this.$message.error(response.message);
        }
      });
    },*/

    async getDBType() {
      return getDBTypeAPI()
        .then((response) => {
          if (response.code == 200) {
            this.databaseTypeList = response.data;
            this.$nextTick(() => {
              if (this.databaseTypeList.length > 0) {
                this.dataSource.dbType = this.databaseTypeList[0].name;
                let selected = this.databaseTypeList[0].name; // 设置默认值并触发change事件
                this.selectDatabaseType(selected); // 直接调用handleChange方法也可以触发change事件监听器中的逻辑
              }
            });
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },

    selectDatabaseType(item) {
      let selectItem = this.databaseTypeList.filter(
        (itemtemp) => itemtemp.name == item
      );

      if (selectItem.length > 0) {
        this.JDBCPrefix = selectItem[0].jdbcPrefix;
        this.JDBCURL = selectItem[0].jdbcPrefix;
        //if (this.dataSource.port == null || this.dataSource.port == "")
        if (this.dataSourceItem == null) {
          this.dataSource.port = selectItem[0].defaultPort;
        }
      }
    },
  },
};
</script>

 <style scoped lang="scss">
.ds-form-wrap {
  padding: 8px 24px 8px;
}

.ds-form__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;

  &::before {
    content: "";
    width: 4px;
    height: 14px;
    border-radius: 2px;
    background: var(
      --accent-gradient,
      linear-gradient(180deg, #3b82f6, #06b6d4)
    );
  }
}

.ds-form {
  ::v-deep .el-form-item__label {
    padding-bottom: 6px;
    font-weight: 600;
    color: #334155;
  }
  ::v-deep .el-form-item {
    margin-bottom: 15px;
  }
}

.ds-form__hint {
  font-weight: 400;
  color: #94a3b8;
  font-size: 12px;
}

.ds-form__jdbc {
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e5eaf1);
  background: #f8fafc;
  font-family: var(--font-mono, Consolas, Menlo, monospace);
  font-size: 12.5px;
  color: #475569;
  word-break: break-all;
  line-height: 1.6;
}

.ds-form__test-ok {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
  font-size: 12.5px;
}

.ds-form__test-fail {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #dc2626;
  font-size: 12.5px;
}

.ds-form__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 0 14px;
  margin-top: 10px;
  border-top: 1px solid var(--border-color, #e5eaf1);
}
</style>