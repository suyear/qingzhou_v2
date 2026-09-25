<template>
  <section class="qz-section">
    <header v-if="title || $slots.extra" class="qz-section__head">
      <div class="qz-section__titles">
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="hint" class="qz-section__hint">{{ hint }}</p>
      </div>
      <div v-if="$slots.extra" class="qz-section__extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="qz-section__body">
      <slot />
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
})
</script>

<style scoped>
.qz-section {
  background: var(--qz-card);
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  box-shadow: var(--qz-shadow);
  overflow: hidden;
}
.qz-section + .qz-section {
  margin-top: 12px;
}
.qz-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--qz-border);
  background: #fff;
}
.qz-section__titles h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  color: var(--qz-text);
}
.qz-section__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.45;
}
.qz-section__extra {
  flex-shrink: 0;
}
.qz-section__body {
  padding: 14px;
}
</style>
