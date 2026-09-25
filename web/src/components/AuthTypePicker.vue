<template>
  <div class="auth-type-picker" :class="{ compact }">
    <div v-if="groups.length > 1" class="auth-type-groups">
      <section v-for="group in groups" :key="group.key" class="auth-type-group">
        <div v-if="group.title" class="auth-type-group-title">{{ group.title }}</div>
        <div class="auth-type-grid" :style="gridStyle">
          <button
            v-for="item in group.items"
            :key="item.value"
            type="button"
            class="auth-type-tile"
            :class="{ active: model === item.value, muted: item.muted }"
            @click="select(item.value)"
          >
            <span class="auth-type-tile-mark" aria-hidden="true">{{ item.mark || item.label.slice(0, 1) }}</span>
            <span class="auth-type-tile-body">
              <span class="auth-type-tile-label">{{ item.label }}</span>
              <span v-if="item.desc" class="auth-type-tile-desc">{{ item.desc }}</span>
            </span>
          </button>
        </div>
      </section>
    </div>
    <div v-else class="auth-type-grid" :style="gridStyle">
      <button
        v-for="item in flatItems"
        :key="item.value"
        type="button"
        class="auth-type-tile"
        :class="{ active: model === item.value, muted: item.muted }"
        @click="select(item.value)"
      >
        <span class="auth-type-tile-mark" aria-hidden="true">{{ item.mark || item.label.slice(0, 1) }}</span>
        <span class="auth-type-tile-body">
          <span class="auth-type-tile-label">{{ item.label }}</span>
          <span v-if="item.desc" class="auth-type-tile-desc">{{ item.desc }}</span>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** @type {{ value: string, label: string, desc?: string, mark?: string, muted?: boolean, group?: string }[]} */
  options: { type: Array, required: true },
  /** 分组：[{ key, title, values: string[] }]；不传则平铺 */
  groupDefs: { type: Array, default: null },
  columns: { type: Number, default: 2 },
  compact: { type: Boolean, default: false },
})

const model = defineModel({ type: String, default: '' })

const flatItems = computed(() => props.options || [])

const groups = computed(() => {
  if (!props.groupDefs?.length) return []
  return props.groupDefs.map((g) => ({
    key: g.key,
    title: g.title,
    items: (props.options || []).filter((item) => (g.values || []).includes(item.value)),
  })).filter((g) => g.items.length)
})

const gridStyle = computed(() => ({
  '--auth-cols': String(Math.max(1, props.columns || 2)),
}))

function select(value) {
  model.value = value
}
</script>

<style scoped>
.auth-type-picker {
  width: 100%;
}
.auth-type-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.auth-type-group-title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--qz-text-muted);
}
.auth-type-grid {
  display: grid;
  grid-template-columns: repeat(var(--auth-cols), minmax(0, 1fr));
  gap: 8px;
}
.auth-type-tile {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  margin: 0;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-card);
  color: var(--qz-text);
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}
.auth-type-tile:hover {
  border-color: #bfdbfe;
  background: var(--qz-primary-soft);
}
.auth-type-tile.active {
  border-color: var(--qz-primary);
  background: var(--qz-primary-soft);
  box-shadow: inset 0 0 0 1px var(--qz-primary);
}
.auth-type-tile.muted:not(.active) {
  background: var(--qz-fill);
}
.auth-type-tile-mark {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  font-family: var(--qz-code-font);
  color: var(--qz-primary);
  background: #dbeafe;
}
.auth-type-tile.active .auth-type-tile-mark {
  color: #fff;
  background: var(--qz-primary);
}
.auth-type-tile-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.auth-type-tile-label {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--qz-text);
}
.auth-type-tile-desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--qz-text-muted);
}
.compact .auth-type-tile {
  padding: 8px 10px;
  gap: 8px;
}
.compact .auth-type-tile-mark {
  width: 24px;
  height: 24px;
  font-size: 10px;
}
.compact .auth-type-tile-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 640px) {
  .auth-type-grid {
    grid-template-columns: 1fr;
  }
}
</style>
