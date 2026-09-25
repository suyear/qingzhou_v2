import { register } from '@antv/x6-vue-shape'
import ComponentNode from './ComponentNode.vue'

let registered = false

export const NODE_SHAPE = 'qz-component-node'

export function registerComponentNode() {
  if (registered) {
    return
  }
  register({
    shape: NODE_SHAPE,
    width: 300,
    height: 148,
    component: ComponentNode,
    ports: {
      groups: {
        in: {
          position: 'left',
          attrs: {
            circle: { r: 5, magnet: true, stroke: '#2563eb', fill: '#fff', strokeWidth: 2 },
          },
        },
        out: {
          position: 'right',
          attrs: {
            circle: { r: 5, magnet: true, stroke: '#2563eb', fill: '#fff', strokeWidth: 2 },
          },
        },
      },
      items: [
        { id: 'in', group: 'in' },
        { id: 'out', group: 'out' },
      ],
    },
  })
  registered = true
}
