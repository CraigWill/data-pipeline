<template>
  <div class="stat-card">
    <div class="stat-label">{{ title }}</div>
    <div class="stat-value">{{ value }}</div>
    <div class="stat-sub">{{ label }}</div>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  value: [String, Number],
  label: String
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 4px;
  padding: 20px 24px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  transition: box-shadow 0.15s;
  border-top: 3px solid #0052CC;
}

.stat-card:hover {
  box-shadow: 0 4px 8px -2px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.stat-label {
  font-size: 11px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 10px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #172B4D;
  line-height: 1.2;
  margin-bottom: 6px;
}

.stat-sub {
  font-size: 12px;
  color: #97A0AF;
}
</style>
