<template>
  <v-item-group multiple v-model="selectedItems">
    <v-row justify="start" ref="content" v-resize="onResize" v-if="hasItems">
      <v-skeleton-loader v-for="(item, index) in items"
                         :key="index"
                         :width="itemWidth"
                         :height="itemHeight"
                         justify-self="start"
                         :loading="item === null"
                         type="card, text"
                         class="ma-3 mx-2"
                         :data-index="index"
                         v-intersect="onElementIntersect"

      >
        <v-item v-slot:default="{ toggle, active }" :value="$_.get(item, 'id', 0)">
          <slot name="item" v-bind:data="{ toggle, active, item, index, itemWidth, preselect: shouldPreselect(), editItem }">
            <item-card
              :item="item"
              :width="itemWidth"
              :selected="active"
              :preselect="shouldPreselect()"
              :onEdit="editItem"
              :onSelected="toggle"
            ></item-card>
          </slot>
        </v-item>
      </v-skeleton-loader>
    </v-row>
    <v-row v-else justify="center">
      <slot name="empty"></slot>
    </v-row>
  </v-item-group>
</template>

<script lang="ts">
import Vue from 'vue'
import { computeCardWidth } from '@/functions/grid-utilities'
import ItemCard from '@/components/ItemCard.vue'
import mixins from 'vue-typed-mixins'
import VisibleElements from '@/mixins/VisibleElements'

export default mixins(VisibleElements).extend({
  name: 'ItemBrowser',
  components: { ItemCard },
  props: {
    items: {
      type: Array,
      required: true,
    },
    selected: {
      type: Array,
      required: true,
    },
    editFunction: {
      type: Function,
    },
    resizeFunction: {
      type: Function,
    },
  },
  data: () => {
    return {
      selectedItems: [],
      width: 150,
    }
  },
  watch: {
    series: {
      handler () {
        this.visibleElements = []
      },
      immediate: true,
    },
    selectedItems: {
      handler () {
        this.$emit('update:selected', this.selectedItems)
      },
      immediate: true,
    },
    selected: {
      handler () {
        this.selectedItems = this.selected as []
      },
      immediate: true,
    },
  },
  computed: {
    hasItems (): boolean {
      return this.items.length > 0
    },
    itemWidth (): number {
      return this.width
    },
    itemHeight (): number {
      return this.width / 0.7071 + 116
    },
  },
  methods: {
    shouldPreselect (): boolean {
      return this.selectedItems.length > 0
    },
    editItem (item: any) {
      this.editFunction(item)
    },
    onResize () {
      const content = this.$refs.content as HTMLElement
      this.width = computeCardWidth(content.clientWidth, this.$vuetify.breakpoint.name)
    },
  },
})
</script>

<style scoped>

</style>
