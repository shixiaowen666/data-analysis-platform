<template>
  <div class="login-bg">
    <div class="login-bg-left">
      <div class="login-bg-left-title">
        <svg
          style="cursor: pointer"
          width="28"
          height="28"
          viewBox="0 0 32 32"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <rect
            x="2"
            y="2"
            width="28"
            height="28"
            rx="8"
            fill="url(#logoGrad)"
          ></rect>
          <path d="M10 18h3.5v6H10v-6z" fill="#fff"></path>
          <path d="M14.25 12h3.5v12h-3.5V12z" fill="#fff"></path>
          <path d="M18.5 15h3.5v9h-3.5v-9z" fill="#fff"></path>
          <path d="M10 8h12v2H10V8z" fill="#fff"></path>
          <defs>
            <linearGradient
              id="logoGrad"
              x1="2"
              y1="2"
              x2="30"
              y2="30"
              gradientUnits="userSpaceOnUse"
            >
              <stop stop-color="#204ce9"></stop>
              <stop offset="1" stop-color="#0ea5e9"></stop>
            </linearGradient>
          </defs>
        </svg>
        <span>数据分析平台</span>
      </div>
      <div class="login-bg-left-content">
        <div class="login-bg-left-tag">
          <span>智能</span>
          ·
          <span>高效</span>
          ·
          <span>安全</span>
        </div>
        <div class="login-bg-left-title-text">
          智能数据
          <span class="title-highlight">分析</span>
        </div>
        <div class="login-bg-left-desc">
          基于数据驱动的智能分析平台，帮助企业洞察趋势。优化
          <br />
          决策、提升效率。
        </div>
        <img
          class="login-bg-left-img"
          src="@/assets/images/login-bg.png"
          alt=""
        />
        <div class="login-bg-left-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                class="icon"
                viewBox="0 0 1024 1024"
                version="1.1"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M867.584 160.192c-149.632-16.928-262.208-57.408-334.592-120.352l-19.04-16.544-20.544 14.656C379.968 118.944 267.776 160 160 160H128v448c0 137.344 121.088 261.92 370.208 380.864l13.088 6.24 13.344-5.728C771.072 883.52 896 755.232 896 608V163.424l-28.416-3.232zM832 608c0 116.8-107.392 223.36-319.328 316.8C299.872 821.024 192 714.464 192 608V222.976c104.672-6.784 211.584-46.688 318.496-118.944C587.232 162.528 695.168 201.536 832 220.256V608z"
                  fill="#5b62ce"
                ></path>
                <path
                  d="M359.776 468.672a32 32 0 1 0-47.968 42.4l121.792 137.824c12.608 14.24 30.176 21.568 47.904 21.568a64.384 64.384 0 0 0 49.696-23.52l197.6-242.72a32 32 0 0 0-49.632-40.416l-197.6 242.688-121.792-137.824z"
                  fill="#5b62ce"
                ></path>
              </svg>
            </div>
            <div class="feature-content">
              <div class="feature-title">数据安全</div>
              <div class="feature-desc">多重安全防护</div>
            </div>
          </div>
          <div class="feature-divider"></div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                class="icon"
                viewBox="0 0 1024 1024"
                version="1.1"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M845.091 405.367c-2.605-5.287-10.845-17.748-31.136-19.297a24.783 24.783 0 0 0-1.889-0.064l-208.995-1.341 53.772-267.273a21.16 21.16 0 0 0 0.358-2.236c1.889-16.742-1.482-30-10.031-39.442-7.15-7.923-17.327-12.268-28.661-12.268-12.979 0-24.264 5.735-27.407 7.493a26.58 26.58 0 0 0-7.377 6.07l-402.097 475.79a24.591 24.591 0 0 0-1.743 2.316c-6.188 9.17-10.21 23.77-2.947 37.205 3.712 6.87 13.288 18.372 35.696 18.372 1.074 0 2.182-0.032 3.37-0.08l185.026-0.128-103.31 299.141a25.432 25.432 0 0 0-1.237 5.287c-0.538 4.234-1.45 18.946 9.25 30.928 7.018 7.844 17.114 12.188 28.464 12.188 6.954 0 14.314-1.58 22.49-4.808a26.973 26.973 0 0 0 9.624-6.517l471.746-499.194a26.448 26.448 0 0 0 2.72-3.402c3.158-4.697 12.848-21.375 4.314-38.74z m-475.54 460.264l86.373-250.1c6.4-16.901 6.302-31.087-0.293-42.205-4.999-8.37-13.516-14.218-22.798-15.591a27.6 27.6 0 0 0-3.974-0.304h-0.016l-181.377 0.128 349.335-413.362-47.827 237.784c-3.387 19.36 3.225 32.045 9.38 39.266 12.978 15.272 32.243 16.47 37.877 16.47l176.688 1.07L369.55 865.632z"
                  fill="#5b62ce"
                ></path>
              </svg>
            </div>
            <div class="feature-content">
              <div class="feature-title">智能高效</div>
              <div class="feature-desc">一键分析报告</div>
            </div>
          </div>
          <div class="feature-divider"></div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                class="icon"
                viewBox="0 0 1024 1024"
                version="1.1"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M927.347 831H128.595V161.038c0-17.644-14.304-31.943-31.947-31.943-17.654 0-31.952 14.299-31.952 31.943V875.93c0 11.028 8.945 18.975 19.979 18.975h842.672c17.654 0 31.957-14.31 31.957-31.953 0-17.649-14.308-31.953-31.957-31.953zM260.304 681.58L449.75 492.133l104.662 104.66a32.64 32.64 0 0 0 2.178 2.41c12.48 12.48 32.703 12.48 45.18 0.005L803.156 397.82l70.68 70.68c12.48 12.481 22.598 8.294 22.598-9.35v-181.81c0-11.028-8.946-19.98-19.98-19.98H694.654c-17.634 0-21.826 10.117-9.351 22.598l72.669 72.675L579.294 531.31 473.278 425.29c-6.474-6.469-15.03-9.584-23.509-9.341-8.493-0.254-17.069 2.862-23.547 9.34L215.12 636.398c-12.475 12.47-12.475 32.703 0 45.178 12.48 12.481 32.708 12.481 45.184 0.005z"
                  fill="#5b62ce"
                ></path>
              </svg>
            </div>
            <div class="feature-content">
              <div class="feature-title">专业可靠</div>
              <div class="feature-desc">企业级解决方案</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="logincon">
      <div class="title">数据分析平台</div>

      <el-form
        ref="loginForm"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        label-position="top"
        @keyup.enter.native="handleLogin"
      >
        <el-form-item label="用户名" prop="username" class="login-form-item">
          <el-input
            v-model="loginForm.username"
            prefix-icon="el-icon-user"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password" class="login-form-item">
          <el-input
            v-model="loginForm.password"
            type="password"
            prefix-icon="el-icon-lock"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item class="login-form-submit">
          <div
            class="loginbt"
            :class="{ 'is-loading': loading }"
            @click="handleLogin"
          >
            {{ loading ? "登录中..." : "登 录" }}
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script>
import { login } from "@/api/login";
import { setToken, setUserInfo } from "@/utils/auth";
import toast from '@/utils/toast';

import JSEncrypt from "jsencrypt";
import { RSA_PUBLIC_KEY } from "@/utils/common";

export default {
  name: "Login",
  data() {
    return {
      loginForm: {
        username: "",
        password: "",
      },
      loginRules: {
        username: [
          { required: true, message: "请输入用户名", trigger: "blur" },
        ],
        password: [{ required: true, message: "请输入密码", trigger: "blur" }],
      },
      loading: false,
      redirect: undefined,
    };
  },
  watch: {
    $route: {
      handler(route) {
        this.redirect = route.query && route.query.redirect;
      },
      immediate: true,
    },
  },
  mounted() {
    //console.log('aa')
    //console.log(window.location.hostname)
    // 如果是从跳转过来的，自动执行登录
    /*let userId = this.$route.query.userId;
    let accountName = this.$route.query.accountName;

    if (userId && accountName) {
      loginTo({
        userId: userId,
        accountName: accountName,
      })
        .then((res) => {
          if (res.code == 1) {
            const { token, user } = this.resolveLoginData(res);
            if (!token) {
              this.$message({
                message: "登录成功但未获取到凭证，请联系管理员",
                type: "error",
                duration: this.$messageDuration,
              });

              return;
            }
            setToken(token);
            if (user) {
              const safeUser = { ...user };
              delete safeUser.password;
              setUserInfo(safeUser);
            }
            this.$router.push({ path: this.redirect || "/" }).catch(() => {});
          } else {
            this.$message({
              message: res.message,
              type: "error",
              duration: this.$messageDuration,
            });
          }
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false;
        });
    }*/
  },

  methods: {
    handleLogin() {
      if (this.loading) {
        return;
      }

      this.$refs.loginForm.validate((valid) => {
        if (!valid) {
          return;
        }

        const encryptor = new JSEncrypt();
        encryptor.setPublicKey(RSA_PUBLIC_KEY);
        const rawText = this.loginForm.password + "|" + Date.now();
        const encryptedPassword = encryptor.encrypt(rawText);

        if (!encryptedPassword) {
          toast.error('密码加密失败')
          return;
        }

        this.loading = true;
        login({
          username: this.loginForm.username,
          password: encryptedPassword,
        })
          .then((res) => {
            if (res.code == 1) {
              const { token, user } = this.resolveLoginData(res);
              if (!token) {

                toast.error('登录成功但未获取到凭证，请联系管理员')

                return;
              }
              setToken(token);
              if (user) {
                const safeUser = { ...user };
                delete safeUser.password;
                setUserInfo(safeUser);

              }
              
              this.$router.push({ path: this.redirect || "/" }).catch(() => {});
            } else {
              toast.error(response.message)
            }
          })
          .catch(() => {})
          .finally(() => {
            this.loading = false;
          });
      });
    },
    resolveLoginData(res) {
      if (!res) {
        return { token: "", user: null };
      }
      const data = res.data;
      if (typeof data === "string") {
        return { token: data, user: null };
      }
      if (data && typeof data === "object") {
        return {
          token:
            data.token ||
            data.accessToken ||
            data.access_token ||
            data.Authorization ||
            "",
          user: data.user || null,
        };
      }
      return {
        token: res.token || res.accessToken || res.access_token || "",
        user: res.user || null,
      };
    },
  },
};
</script>

<style scoped lang="scss">
.login-bg {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background-image: radial-gradient(
      ellipse at 15% 30%,
      rgba(248, 234, 252, 0.8) 0%,
      transparent 45%
    ),
    radial-gradient(
      ellipse at 85% 70%,
      rgba(226, 238, 255, 0.8) 0%,
      transparent 45%
    ),
    radial-gradient(
      ellipse at 50% 10%,
      rgba(255, 255, 255, 0.9) 0%,
      transparent 50%
    );
  background-size: 100% 100%;
  width: 100vw;
  height: 100vh;

  .login-bg-left {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    .login-bg-left-title {
      position: absolute;
      top: 20px;
      left: 20px;
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 20px;
      font-weight: 500;
      color: #333;
    }
  }

  .login-bg-left-content {
    position: absolute;
    top: 50%;
    left: 25%;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;

    .login-bg-left-tag {
      display: grid;
      grid-template-columns: 1fr 0.5fr 1fr 0.5fr 1fr;
      background: #e8deff;
      color: #5a63c1;
      padding: 6px 18px;
      border-radius: 20px;
      font-size: 12px;
      margin-bottom: 10px;
    }

    .login-bg-left-title-text {
      font-size: 30px;
      font-weight: 500;
      color: #1f2937;
      margin-bottom: 10px;

      .title-highlight {
        color: #5a63c1;
      }
    }

    .login-bg-left-desc {
      font-size: 14px;
      color: #6b7280;
      max-width: 600px;
      margin-bottom: 10px;
    }

    .login-bg-left-img {
      width: 40vw;
      max-width: 560px;
      margin: 10px 0;
    }

    .login-bg-left-features {
      display: flex;
      gap: 28px;
      margin-top: 10px;

      .feature-item {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 12px;

        .feature-icon {
          width: 20px;
          height: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #3b82f6;
          flex-shrink: 0;

          svg {
            width: 30px;
            height: 30px;
          }
        }

        .feature-content {
          display: flex;
          flex-direction: column;
          gap: 4px;
        }

        .feature-title {
          font-size: 16px;
          font-weight: 600;
          color: #1f2937;
        }

        .feature-desc {
          font-size: 14px;
          color: #6b7280;
        }
      }

      .feature-divider {
        width: 1px;
        background: linear-gradient(
          to bottom,
          transparent,
          #d1d5db,
          transparent
        );
      }
    }
  }
}

.logincon {
  position: fixed;
  right: 250px;
  top: 50%;
  margin-top: -180px;
  width: 480px;
  padding: 25px 15px;
  background-color: #fff;
  text-align: center;
  box-shadow: 0 0 10px 0 rgba(0, 0, 0, 0.05);

  .title {
    font-family: "PingFang SC", sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 20px;
    line-height: 24px;
    text-align: center;
    letter-spacing: 1.39682px;
    color: #000;
  }

  .login-form {
    width: 400px;
    margin: 24px auto 0;
    text-align: left;

    ::v-deep .el-form-item__label {
      font-size: 14px;
      font-weight: 500;
      color: #111827;
      line-height: 1;
      padding-bottom: 10px;
    }

    ::v-deep .el-input__inner {
      height: 46px;
      line-height: 46px;
      font-size: 16px;
      border-radius: 12px;
    }

    ::v-deep .el-input__prefix {
      color: #9ca3af;
    }

    .login-form-item {
      margin-bottom: 22px;
    }

    .login-form-submit {
      margin-bottom: 0;

      ::v-deep .el-form-item__content {
        line-height: normal;
      }
    }
  }

  .loginbt {
    text-align: center;
    margin-top: 24px;
    width: 400px;
    height: 40px;
    display: inline-block;
    line-height: 40px;
    background: linear-gradient(270deg, #204ce9 2.5%, #3b82f6 100%);
    border-radius: 4px;
    color: #fff;
    cursor: pointer;
    font-size: 16px;
    user-select: none;
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.9;
    }

    &.is-loading {
      opacity: 0.7;
      cursor: not-allowed;
      pointer-events: none;
    }
  }
}
</style>
