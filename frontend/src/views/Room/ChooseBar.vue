<script setup>
import { computed } from 'vue'

const props = defineProps({
  options: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const model = computed({
  get: () => props.modelValue,
  set: (v) => {
    if (!props.disabled) {
      emit('update:modelValue', v)
    }
  }
})

const handleClick = (opt) => {
  if (!props.disabled) {
    model.value = opt.key
  }
}
</script>

<template>
  <div class="space-y-3">
    <button
      v-for="opt in options"
      :key="opt.key"
      @click="handleClick(opt)"
      :disabled="disabled"
      class="w-full px-6 py-4 rounded-xl text-left transition-all
             border-2 font-medium
             disabled:cursor-not-allowed"
      :class="model === opt.key
        ? 'bg-blue-100 border-blue-500 text-blue-700 dark:bg-blue-900/30 dark:border-blue-600 dark:text-blue-300'
        : 'bg-gray-50 border-gray-200 text-gray-700 hover:border-blue-300 hover:bg-blue-50 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-300 dark:hover:border-blue-600 dark:hover:bg-gray-600'"
    >
      <span class="inline-flex items-center gap-3">
        <span class="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-sm"
              :class="model === opt.key
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 text-gray-600 dark:bg-gray-600 dark:text-gray-300'">
          {{ opt.key }}
        </span>
        <span>{{ opt.text }}</span>
      </span>
    </button>
  </div>
</template>