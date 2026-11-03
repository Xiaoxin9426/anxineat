Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    text: { type: String, value: '暂无数据' },
    icon: { type: String, value: '🫧' },
    buttonText: { type: String, value: '' }
  },
  methods: {
    onTap() { this.triggerEvent('tap') }
  }
})
