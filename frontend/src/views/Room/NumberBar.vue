<script setup>
import InputNumber from 'primevue/inputnumber'
import { computed } from 'vue'

const emit = defineEmits(['update:modelValue'])
const props = defineProps({
  maxval: {
    type: Number,
    default: 10
  },
  minval: {
    type: Number,
    default: 0
  },
  step: {
    type: Number,
    default: 1
  },
  modelValue: {
    type: Number,
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const model = computed({
  get: () => props.modelValue,
  set: (v) => {
    if (!props.disabled) {
      const n = (v === null || v === '') ? null : Number(v)
      emit('update:modelValue', n)
    }
  }
})
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
      <span>最小值: {{ minval }}</span>
      <span>步长: {{ step }}</span>
      <span>最大值: {{ maxval }}</span>
    </div>
    
    <div class="flex justify-center items-center">
      <InputNumber 
        v-model="model" 
        :step="step"
        :min="minval"
        :max="maxval"
        :disabled="disabled"
        class="custom-input-number"
      />
    </div>
  </div>
</template>

<style scoped>
.custom-input-number {
  max-width: 300px; /* 限制最大宽度，避免太宽 */
}

:deep(.custom-input-number input) {
  width: 100%;
  text-align: center;
  font-size: 1.5rem;
  font-weight: bold;
  padding: 1rem;
  background-color: white;
  color: #111827;
  border: 2px solid #d1d5db;
  border-radius: 0.5rem;
}

:deep(.custom-input-number input:focus) {
  outline: none;
  border-color: #3b82f6;
}

/* 深色模式 */
.dark :deep(.custom-input-number input) {
  background-color: #374151;
  color: white;
  border-color: #4b5563;
}

.dark :deep(.custom-input-number input:focus) {
  border-color: #60a5fa;
}

</style>