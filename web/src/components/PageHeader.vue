<template>
  <div class="page-header">
    <div class="titles">
      <h2>{{ title }}</h2>
      <p v-if="desc" class="desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.default" class="actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 20px;
}
.titles {
  min-width: 0;
  flex: 1;
}
.titles h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 700;
  letter-spacing: -0.025em;
  color: var(--qz-text);
}
.desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--qz-text-muted);
  line-height: 1.6;
  max-width: 640px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
  padding-top: 2px;
}
@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .actions {
    padding-top: 0;
  }
}
</style>
