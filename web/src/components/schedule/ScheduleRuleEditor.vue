<template>
  <div class="schedule-rule-editor">
    <div class="type-cards">
      <button
        v-for="item in SCHEDULE_TYPES"
        :key="item.value"
        type="button"
        class="type-card"
        :class="{ active: model.scheduleType === item.value }"
        @click="setType(item.value)"
      >
        <strong>{{ item.label }}</strong>
        <span>{{ item.desc }}</span>
      </button>
    </div>

    <div v-if="model.scheduleType === 'INTERVAL'" class="rule-panel">
      <div class="inline-fields">
        <span class="field-label">每隔</span>
        <el-input-number v-model="model.intervalValue" :min="1" :max="9999" controls-position="right" />
        <el-select v-model="model.intervalUnit" style="width: 110px">
          <el-option v-for="item in INTERVAL_UNITS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span class="field-label">执行一次</span>
      </div>
      <p class="hint">最短间隔 10 秒，适合「每 30 分钟同步一次」等场景</p>
    </div>

    <div v-else-if="model.scheduleType === 'DAILY'" class="rule-panel">
      <div class="inline-fields">
        <span class="field-label">每天</span>
        <el-time-picker v-model="dailyTimeValue" format="HH:mm" value-format="HH:mm" placeholder="选择时刻" />
        <span class="field-label">执行</span>
      </div>
    </div>

    <div v-else-if="model.scheduleType === 'WEEKLY'" class="rule-panel">
      <div class="week-days">
        <button
          v-for="item in WEEK_OPTIONS"
          :key="item.value"
          type="button"
          class="week-chip"
          :class="{ active: model.weekDays.includes(item.value) }"
          @click="toggleWeekDay(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="inline-fields">
        <span class="field-label">于</span>
        <el-time-picker v-model="dailyTimeValue" format="HH:mm" value-format="HH:mm" placeholder="选择时刻" />
        <span class="field-label">执行</span>
      </div>
    </div>

    <div v-else-if="model.scheduleType === 'ONCE'" class="rule-panel">
      <div class="inline-fields">
        <span class="field-label">在</span>
        <el-date-picker
          v-model="model.fireAt"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm"
          placeholder="选择日期时间"
          style="width: 220px"
        />
        <span class="field-label">执行一次后自动停止</span>
      </div>
    </div>

    <div v-else-if="model.scheduleType === 'CRON'" class="rule-panel">
      <el-input v-model="model.cronExpr" placeholder="秒 分 时 日 月 周，例如 0 */5 * * * *" />
      <div class="cron-presets">
        <el-button
          v-for="item in CRON_PRESETS"
          :key="item.cron"
          size="small"
          @click="model.cronExpr = item.cron"
        >
          {{ item.label }}
        </el-button>
      </div>
      <p class="hint">Spring 6 位 Cron（秒 分 时 日 月 周），时区 Asia/Shanghai</p>
    </div>

    <div class="advanced-block">
      <button type="button" class="advanced-toggle" @click="showAdvanced = !showAdvanced">
        {{ showAdvanced ? '收起自定义高级' : '自定义高级（Cron）' }}
      </button>
      <div v-if="showAdvanced" class="advanced-body">
        <p class="hint">需要复杂规则时，切换到 Cron 表达式；自然语言规则也可查看等效 Cron。</p>
        <el-button
          v-if="model.scheduleType !== 'CRON'"
          size="small"
          type="primary"
          plain
          @click="setType('CRON')"
        >
          使用 Cron 表达式
        </el-button>
        <el-button
          v-else
          size="small"
          plain
          @click="setType('INTERVAL')"
        >
          回到自然语言规则
        </el-button>
        <p v-if="previewCron" class="preview-cron advanced-cron">等效 Cron：{{ previewCron }}</p>
      </div>
    </div>

    <div class="preview-box">
      <div class="preview-head">
        <span>触发预览</span>
        <el-button size="small" :loading="previewing" @click="runPreview">刷新预览</el-button>
      </div>
      <p v-if="previewSummary" class="preview-summary">{{ previewSummary }}</p>
      <ul v-if="previewTimes.length" class="preview-times">
        <li v-for="(time, index) in previewTimes" :key="index">{{ formatTime(time) }}</li>
      </ul>
      <p v-else-if="previewLoaded" class="hint">暂无未来触发时间，请检查规则配置</p>
      <p v-else class="hint">保存前可先刷新，查看接下来几次触发时间</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { formatTime } from '@/utils/format'
import { previewSchedule } from '@/api/schedule'
import {
  CRON_PRESETS,
  INTERVAL_UNITS,
  SCHEDULE_TYPES,
  WEEK_OPTIONS,
  previewPayload,
} from '@/utils/scheduleRule'

const model = defineModel({ type: Object, required: true })

const previewing = ref(false)
const previewLoaded = ref(false)
const previewSummary = ref('')
const previewCron = ref('')
const previewTimes = ref([])
const showAdvanced = ref(false)

const dailyTimeValue = computed({
  get: () => model.value.dailyTime,
  set: (value) => {
    model.value.dailyTime = value || '08:00'
  },
})

function setType(type) {
  model.value.scheduleType = type
  if (type === 'CRON') {
    showAdvanced.value = true
  }
}

function toggleWeekDay(day) {
  const days = [...(model.value.weekDays || [])]
  const index = days.indexOf(day)
  if (index >= 0) {
    days.splice(index, 1)
  } else {
    days.push(day)
    days.sort((a, b) => a - b)
  }
  model.value.weekDays = days
}

async function runPreview() {
  previewing.value = true
  try {
    const res = await previewSchedule(previewPayload(model.value))
    previewSummary.value = res.data?.summary || ''
    previewCron.value = res.data?.effectiveCron || ''
    previewTimes.value = res.data?.nextTimes || []
    previewLoaded.value = true
  } catch {
    previewSummary.value = ''
    previewCron.value = ''
    previewTimes.value = []
    previewLoaded.value = true
  } finally {
    previewing.value = false
  }
}

watch(
  () => [
    model.value.scheduleType,
    model.value.cronExpr,
    model.value.intervalValue,
    model.value.intervalUnit,
    model.value.fireAt,
    model.value.dailyTime,
    JSON.stringify(model.value.weekDays || []),
  ],
  () => {
    previewLoaded.value = false
  },
)

watch(
  () => model.value.scheduleType,
  (type) => {
    if (type === 'CRON') showAdvanced.value = true
  },
  { immediate: true },
)
</script>

<style scoped>
.schedule-rule-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.type-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 8px;
}

.type-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.type-card strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.type-card span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.35;
}

.type-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}

.rule-panel {
  padding: 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.inline-fields {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.week-days {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.week-chip {
  min-width: 52px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
}

.week-chip.active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.cron-presets {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.advanced-block {
  border: 1px dashed var(--qz-border);
  border-radius: var(--qz-radius-sm);
  padding: 8px 12px;
}

.advanced-toggle {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.advanced-body {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.advanced-cron {
  margin: 0;
}

.preview-box {
  padding: 12px 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-fill);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}

.preview-summary {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.preview-cron {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.preview-times {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.preview-times li {
  margin: 2px 0;
}
</style>
