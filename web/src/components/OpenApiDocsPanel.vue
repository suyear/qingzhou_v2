<template>
  <div class="openapi-docs">
    <div class="docs-hero">
      <div class="docs-hero-title">外部系统如何调用你的工作流？</div>
      <p class="docs-hero-desc">不会写签名也没关系：在「应用管理」打开<strong>调用助手</strong>，填入参后点「生成 curl」即可复制到终端。下方是给对接同学的完整签名说明。</p>
    </div>

    <section class="doc-section">
      <h3>快速开始</h3>
      <ol class="steps">
        <li>在工作流编排中<strong>发布</strong>工作流，记下 <code>workflowCode</code></li>
        <li>在「应用管理」<strong>新建应用</strong>，保存 App Key 和 Secret（Secret 只显示一次）</li>
        <li><strong>授权工作流</strong> → 打开「调用助手」填表单或 JSON，生成 curl 或走网关试调</li>
        <li>正式对接时按下方签名规则在服务端计算 HMAC（不要把 Secret 放到浏览器）</li>
      </ol>
    </section>

    <section class="doc-section">
      <h3>调用地址</h3>
      <DetailCodeBlock title="接口" value="POST {baseUrl}/openapi/v1/workflows/{workflowCode}/execute" tone="ink" max-height="120px" />
      <p class="tip">控制台「调用助手」生成的 curl 已包含正确地址和签名头。生产环境请在服务端配置 <code>qingzhou.openapi.public-base-url</code>。</p>
    </section>

    <section class="doc-section">
      <h3>请求头</h3>
      <el-table :data="headers" size="small" border class="doc-table">
        <el-table-column prop="name" label="Header" width="140" />
        <el-table-column prop="desc" label="说明" />
      </el-table>
    </section>

    <section class="doc-section">
      <h3>签名算法</h3>
      <DetailCodeBlock
        title="算法"
        :value="signSample"
        copy-message="已复制签名算法"
        tone="ink"
        max-height="140px"
      />
      <ul class="bullets">
        <li><code>body</code> 必须是<strong>紧凑 JSON</strong>（无多余空格），空对象写 <code>{}</code></li>
        <li><code>timestamp</code> 为毫秒时间戳，允许 ±5 分钟误差</li>
        <li><code>nonce</code> 为随机串，10 分钟内不可重复（防重放）</li>
      </ul>
    </section>

    <section class="doc-section">
      <h3>代码示例</h3>
      <el-tabs v-model="sampleTab" class="sample-tabs">
        <el-tab-pane label="curl" name="curl">
          <DetailCodeBlock title="curl" :value="curlSample" copy-message="已复制" tone="ink" max-height="280px" />
        </el-tab-pane>
        <el-tab-pane label="Python" name="python">
          <DetailCodeBlock title="Python" :value="pythonSample" copy-message="已复制" tone="ink" max-height="360px" />
        </el-tab-pane>
        <el-tab-pane label="Node.js" name="node">
          <DetailCodeBlock title="Node.js" :value="nodeSample" copy-message="已复制" tone="ink" max-height="360px" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <section class="doc-section">
      <h3>常见错误</h3>
      <el-table :data="errors" size="small" border class="doc-table">
        <el-table-column prop="code" label="现象" width="160" />
        <el-table-column prop="fix" label="处理建议" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'

const sampleTab = ref('curl')

const headers = [
  { name: 'X-App-Key', desc: '应用 App Key，如 ak_xxxxxxxx' },
  { name: 'X-Timestamp', desc: '毫秒时间戳' },
  { name: 'X-Nonce', desc: '随机字符串，每次请求唯一' },
  { name: 'X-Signature', desc: '按签名算法计算的 HMAC-SHA256 十六进制小写' },
  { name: 'Content-Type', desc: 'application/json' },
]

const errors = [
  { code: '签名不匹配', fix: '检查 body 是否为紧凑 JSON；Secret 是否正确；stringToSign 拼接顺序' },
  { code: '时间戳超出窗口', fix: '校准服务器时钟，timestamp 使用当前毫秒时间' },
  { code: 'Nonce 重复', fix: '每次请求生成新的 nonce' },
  { code: '应用未授权该工作流', fix: '在应用管理中授权对应已发布工作流' },
  { code: '工作流未发布', fix: '先在编排页发布工作流' },
  { code: 'IP 不在白名单', fix: '在应用设置中调整 IP 白名单或清空不限制' },
  { code: '超过 QPS 限制', fix: '调低调用频率或提高应用 QPS 上限' },
]

const signSample = `stringToSign = MD5(body) + timestamp + nonce + secret
signature    = Hex(HMAC-SHA256(key=secret, data=stringToSign)).toLowerCase()`

const curlSample = `curl -X POST 'https://your-host/openapi/v1/workflows/demo_flow/execute' \\
  -H 'Content-Type: application/json' \\
  -H 'X-App-Key: ak_xxxxxxxx' \\
  -H 'X-Timestamp: 1710000000000' \\
  -H 'X-Nonce: abc123' \\
  -H 'X-Signature: <按算法计算>' \\
  -d '{"orderId":"10001"}'`

const pythonSample = `import hashlib, hmac, json, time, uuid, requests

secret = "your-secret"
body = json.dumps({"orderId": "10001"}, separators=(",", ":"))
ts = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
md5 = hashlib.md5(body.encode()).hexdigest()
sign = hmac.new(secret.encode(), f"{md5}{ts}{nonce}{secret}".encode(), hashlib.sha256).hexdigest()

requests.post(
    "https://your-host/openapi/v1/workflows/demo_flow/execute",
    data=body,
    headers={
        "Content-Type": "application/json",
        "X-App-Key": "ak_xxxxxxxx",
        "X-Timestamp": ts,
        "X-Nonce": nonce,
        "X-Signature": sign,
    },
)`

const nodeSample = `import crypto from 'crypto'

const secret = 'your-secret'
const body = JSON.stringify({ orderId: '10001' })
const ts = String(Date.now())
const nonce = crypto.randomBytes(8).toString('hex')
const md5 = crypto.createHash('md5').update(body).digest('hex')
const sign = crypto.createHmac('sha256', secret).update(md5 + ts + nonce + secret).digest('hex')

await fetch('https://your-host/openapi/v1/workflows/demo_flow/execute', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-App-Key': 'ak_xxxxxxxx',
    'X-Timestamp': ts,
    'X-Nonce': nonce,
    'X-Signature': sign,
  },
  body,
})`

</script>

<style scoped>
.openapi-docs { padding: 4px 0 16px; }
.docs-hero {
  padding: 16px 18px;
  margin-bottom: 20px;
  border-radius: var(--qz-radius);
  background: var(--qz-primary-soft);
  border: 1px solid var(--el-color-primary-light-7);
}
.docs-hero-title { font-size: 16px; font-weight: 700; margin-bottom: 6px; }
.docs-hero-desc { margin: 0; font-size: 13px; color: var(--qz-text-muted); line-height: 1.6; }
.doc-section { margin-bottom: 24px; }
.doc-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
}
.steps {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
  color: var(--qz-text-muted);
  font-size: 14px;
}
.bullets {
  margin: 8px 0 0;
  padding-left: 20px;
  line-height: 1.7;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.5;
}
.doc-table { margin-top: 8px; }
.sample-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--qz-fill);
  font-size: 12px;
}
</style>
