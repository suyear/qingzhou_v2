<template>
  <div class="openapi-docs">
    <div class="docs-hero">
      <div class="docs-hero-title">如何调用开放接口服务与组合接口服务？</div>
      <p class="docs-hero-desc">
        接口组件 = 接口服务；工作流编排 = 组合接口服务。授权给开放应用后，用同一套签名调用。
        下方示例与「调用助手」走网关试调看到的<strong>请求入参 / 业务 output</strong>结构一致（助手页外层的 preview 包装仅用于控制台代理，不是对外契约）。
      </p>
    </div>

    <section class="doc-section">
      <h3>快速开始</h3>
      <ol class="steps">
        <li>
          准备可开放资源：启用<strong>接口组件</strong>或发布<strong>工作流</strong>。
          <code>componentCode</code> 在「接口组件」列表的<strong>编码</strong>列（点击可复制）；
          <code>workflowCode</code> 在「工作流编排」列表的<strong>编码</strong>列。
          开放平台授权/调用助手下拉里也会显示编码。
        </li>
        <li>在「应用管理」<strong>新建应用</strong>，保存 App Key 和 Secret（Secret 只显示一次）</li>
        <li><strong>授权资源</strong> → 打开「调用助手」，按资源 schema 填入参，生成 curl 或走网关试调</li>
        <li>正式对接时按下方签名规则在服务端计算 HMAC（不要把 Secret 放到浏览器）</li>
      </ol>
    </section>

    <section class="doc-section">
      <h3>调用地址（双路径）</h3>
      <DetailCodeBlock title="接口服务（组件）" :value="componentPath" tone="ink" max-height="80px" />
      <DetailCodeBlock class="mt10" title="组合接口服务（工作流）" :value="workflowPath" tone="ink" max-height="80px" />
      <p class="tip">
        路径中的 <code>{componentCode}</code> / <code>{workflowCode}</code> 即列表「编码」列的值
        （例如 <code>custom.db.id</code>、<code>wf_1790318130929</code>）。
        调用助手生成的 curl 已填好真实编码。生产环境请在
        <strong>系统设置 → 开放平台对外地址</strong> 配置（对应 <code>qingzhou.openapi.public-base-url</code>）。
      </p>
    </section>

    <section class="doc-section">
      <h3>请求体</h3>
      <p class="tip">
        Body 为资源<strong>入参扁平 JSON</strong>（不要再包一层 <code>input</code>），字段以该资源 schema 为准
        （调用助手「入参字段说明」同源）。下方 demo 对应「按 userId 查询」类资源。
      </p>
      <DetailCodeBlock title="示例请求（与 curl -d / 助手试调入参同源）" :value="bodyPretty" tone="ink" max-height="100px" />
    </section>

    <section class="doc-section">
      <h3>成功响应</h3>
      <el-radio-group v-model="samplePath" size="small" class="path-switch">
        <el-radio-button value="workflow">组合接口服务</el-radio-button>
        <el-radio-button value="component">接口服务</el-radio-button>
      </el-radio-group>
      <p class="tip">
        外层 <code>{ code, message, data }</code>；业务在 <code>data.output</code>；无编排 <code>steps</code>。
        DB 查询结果为 <code>rows</code>（不是执行日志里的 <code>preview</code>）。
        组件调用无 <code>executionId</code> / <code>executionNo</code>。
      </p>
      <DetailCodeBlock
        :title="samplePath === 'component' ? '示例响应 · 接口服务' : '示例响应 · 组合接口服务'"
        :value="successSampleText"
        tone="ink"
        max-height="360px"
      />
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
        title="算法（组件与工作流共用）"
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
      <el-radio-group v-model="samplePath" size="small" class="path-switch">
        <el-radio-button value="workflow">组合接口服务</el-radio-button>
        <el-radio-button value="component">接口服务</el-radio-button>
      </el-radio-group>
      <p class="tip">以下 body 均为 <code>{{ bodyCompact }}</code>，与「请求体」及调用助手试调入参一致。</p>
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
        <el-table-column prop="code" label="现象" width="180" />
        <el-table-column prop="fix" label="处理建议" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'
import {
  OPENAPI_DEMO_INPUT,
  openapiBodyCompact,
  openapiBodyPretty,
  openapiSuccessSampleText,
} from '@/utils/openapiContract'

const sampleTab = ref('curl')
const samplePath = ref('workflow')

const bodyCompact = openapiBodyCompact()
const bodyPretty = openapiBodyPretty()
const successSampleText = computed(() => openapiSuccessSampleText(samplePath.value))

const componentPath = 'POST {baseUrl}/openapi/v1/components/{componentCode}/execute'
const workflowPath = 'POST {baseUrl}/openapi/v1/workflows/{workflowCode}/execute'

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
  { code: '应用未授权该资源', fix: '在应用管理中授权对应接口组件或已发布工作流' },
  { code: '工作流未发布 / 组件未启用', fix: '先发布工作流，或将组件状态设为启用' },
  { code: '缺少入参 / SQL 参数', fix: '对照资源 schema 或调用助手「入参字段说明」补齐必填字段' },
  { code: 'IP 不在白名单', fix: '在应用设置中调整 IP 白名单或清空不限制' },
  { code: '超过 QPS 限制', fix: '调低调用频率或提高应用 QPS 上限' },
]

const signSample = `stringToSign = MD5(body) + timestamp + nonce + secret
signature    = Hex(HMAC-SHA256(key=secret, data=stringToSign)).toLowerCase()`

const executeUrl = computed(() =>
  samplePath.value === 'component'
    ? 'https://your-host/openapi/v1/components/demo_api/execute'
    : 'https://your-host/openapi/v1/workflows/demo_flow/execute',
)

const curlSample = computed(() => `curl -X POST '${executeUrl.value}' \\
  -H 'Content-Type: application/json' \\
  -H 'X-App-Key: ak_xxxxxxxx' \\
  -H 'X-Timestamp: 1710000000000' \\
  -H 'X-Nonce: abc123' \\
  -H 'X-Signature: <按算法计算>' \\
  -d '${bodyCompact}'`)

const pythonSample = computed(() => `import hashlib, hmac, json, time, uuid, requests

secret = "your-secret"
body = json.dumps(${JSON.stringify(OPENAPI_DEMO_INPUT)}, separators=(",", ":"))
ts = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
md5 = hashlib.md5(body.encode()).hexdigest()
sign = hmac.new(secret.encode(), f"{md5}{ts}{nonce}{secret}".encode(), hashlib.sha256).hexdigest()

requests.post(
    "${executeUrl.value}",
    data=body,
    headers={
        "Content-Type": "application/json",
        "X-App-Key": "ak_xxxxxxxx",
        "X-Timestamp": ts,
        "X-Nonce": nonce,
        "X-Signature": sign,
    },
)`)

const nodeSample = computed(() => `import crypto from 'crypto'

const secret = 'your-secret'
const body = JSON.stringify(${JSON.stringify(OPENAPI_DEMO_INPUT)})
const ts = String(Date.now())
const nonce = crypto.randomBytes(8).toString('hex')
const md5 = crypto.createHash('md5').update(body).digest('hex')
const sign = crypto.createHmac('sha256', secret).update(md5 + ts + nonce + secret).digest('hex')

await fetch('${executeUrl.value}', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-App-Key': 'ak_xxxxxxxx',
    'X-Timestamp': ts,
    'X-Nonce': nonce,
    'X-Signature': sign,
  },
  body,
})`)
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
  margin: 8px 0 10px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.5;
}
.doc-table { margin-top: 8px; }
.path-switch { margin-bottom: 8px; }
.sample-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
.mt10 { margin-top: 10px; }
code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--qz-fill);
  font-size: 12px;
}
</style>
