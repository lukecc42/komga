<template>
  <v-container class="ma-0 pa-0 full-height" fluid v-if="pages.length > 0"
               :style="`width: 100%; background-color: ${backgroundColor}`"
               v-touch="{
                 left: () => {if(swipe) {turnRight()}},
                 right: () => {if(swipe) {turnLeft()}},
                 up: () => {if(swipe) {verticalNext()}},
                 down: () => {if(swipe) {verticalPrev()}}
                 }"
  >
    <div>
      <v-slide-y-transition>
        <v-toolbar
          dense elevation="1"
          v-if="toolbar"
          class="settings full-width"
          style="position: fixed; top: 0"
        >
          <v-btn
            icon
            @click="closeBook"
          >
            <v-icon>mdi-arrow-left</v-icon>
          </v-btn>
          <v-toolbar-title> {{ bookTitle }}</v-toolbar-title>
          <v-spacer></v-spacer>
          <v-btn
            icon
            @click="showThumbnailsExplorer = !showThumbnailsExplorer"
          >
            <v-icon>mdi-view-grid</v-icon>
          </v-btn>
          <v-btn
            icon
            @click="menu = !menu"
          >
            <v-icon>mdi-settings</v-icon>
          </v-btn>
        </v-toolbar>
      </v-slide-y-transition>
      <v-slide-y-reverse-transition>
        <v-toolbar
          dense
          elevation="1"
          class="settings full-width"
          style="position: fixed; bottom: 0"
          horizontal
          v-if="toolbar"
        >
          <v-row justify="center">
            <!--  Menu: page slider  -->
            <v-col>
              <v-slider
                hide-details
                thumb-label
                @change="goTo"
                v-model="goToPage"
                class="align-center"
                min="1"
                :max="pagesCount"
              >
                <template v-slot:prepend>
                  <v-icon @click="goToFirst" class="mx-2">mdi-arrow-collapse-left</v-icon>
                  <v-label>
                    {{ currentPage }}
                  </v-label>
                </template>
                <template v-slot:append>
                  <v-label>
                    {{ pagesCount }}
                  </v-label>
                  <v-icon @click="goToLast" class="mx-2">mdi-arrow-collapse-right</v-icon>
                </template>
              </v-slider>
              <!--              <v-dialog v-model="dialogGoto" persistent max-width="290">-->
              <!--                <template v-slot:activator="{ on }">-->
              <!--                  <v-icon large class="ma-auto" v-on="on">mdi-arrow-right-bold-circle</v-icon>-->
              <!--                </template>-->
              <!--                <v-card >-->
              <!--                  <v-card-text class="d-flex flex-row">-->
              <!--                    <v-text-field-->
              <!--                      v-model="goToPage"-->
              <!--                      hide-details-->
              <!--                      single-line-->
              <!--                      type="number"-->
              <!--                      autofocus-->
              <!--                    />-->
              <!--                  </v-card-text>-->

              <!--                  <v-card-actions>-->
              <!--                    <v-spacer></v-spacer>-->
              <!--                    <v-btn color="darken-1" text @click="dialogGoto = false">Close</v-btn>-->
              <!--                    <v-btn color="green darken-1" text @click="goTo(goToPage)">Go To</v-btn>-->
              <!--                  </v-card-actions>-->
              <!--                </v-card>-->
              <!--              </v-dialog>-->
            </v-col>
          </v-row>

        </v-toolbar>
      </v-slide-y-reverse-transition>
    </div>

    <!--  clickable zone: left  -->
    <div @click="turnLeft()"
         class="left-quarter full-height top"
         style="z-index: 1;"
    />

    <!--  clickable zone: menu  -->
    <div @click="toolbar = !toolbar"
         class="center-half full-height top"
         style="z-index: 1;"
    />

    <!--  clickable zone: right  -->
    <div @click="turnRight()"
         class="right-quarter full-height top"
         style="z-index: 1;"
    />

    <div class="full-height">
      <!--  Carousel  -->
      <v-carousel v-model="carouselPage"
                  :show-arrows="false"
                  :continuous="false"
                  :reverse="flipDirection"
                  :vertical="vertical"
                  hide-delimiters
                  touchless
                  height="100%"
      >
        <!--  Carousel: pages  -->
        <v-carousel-item v-for="p in slidesRange"
                         :key="doublePages ? `db${p}` : `sp${p}`"
                         :eager="eagerLoad(p)"
                         class="full-height"
                         :transition="animations ? undefined : false"
                         :reverse-transition="animations ? undefined : false"
        >
          <div class="full-height d-flex flex-column justify-center">
            <div :class="`d-flex flex-row${flipDirection ? '-reverse' : ''} justify-center px-0 mx-0` ">
              <img :src="getPageUrl(p)"
                   :height="maxHeight"
                   :width="maxWidth(p)"
                   :style="imgStyle"
              />
              <img v-if="doublePages && p !== 1 && p !== pagesCount && p+1 !== pagesCount"
                   :src="getPageUrl(p+1)"
                   :height="maxHeight"
                   :width="maxWidth(p+1)"
                   :style="imgStyle"
              />
            </div>
          </div>
        </v-carousel-item>
      </v-carousel>
    </div>

    <thumbnail-explorer-dialog
      v-model="showThumbnailsExplorer"
      :bookId="bookId"
      @goToPage="goTo"
      :pagesCount="pagesCount"
    ></thumbnail-explorer-dialog>

    <v-bottom-sheet
      v-model="menu"
      :close-on-content-click="false"
      :width="$vuetify.breakpoint.width * ($vuetify.breakpoint.smAndUp ? 0.5 : 1)"
      @keydown.esc.stop=""
    >
      <v-container fluid class="pa-0">
        <v-toolbar dark color="primary">
          <v-btn icon dark @click="menu = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
          <v-toolbar-title>Reader Settings</v-toolbar-title>
        </v-toolbar>

        <v-list class="full-height full-width">
          <v-list-item>
            <settings-switch v-model="doublePages" label="Page Layout"
                             :status="`${ doublePages ? 'Double Pages' : 'Single Page'}`"></settings-switch>
          </v-list-item>

          <v-list-item>
            <settings-switch v-model="animations" label="Page Transitions"></settings-switch>
          </v-list-item>

          <v-list-item>
            <settings-switch v-model="swipe" label="Swipe navigation"></settings-switch>
          </v-list-item>

          <v-list-item>
            <settings-select
              :items="backgroundColors"
              v-model="backgroundColor"
              label="Background color"
            >
            </settings-select>
          </v-list-item>

          <v-list-item>
            <settings-select
              :items="readingDirs"
              v-model="readingDirection"
              label="Reading Direction"
            />
          </v-list-item>

          <v-list-item>
            <settings-select
              :items="imageFits"
              v-model="imageFit"
              label="Scaling"
            />
          </v-list-item>
        </v-list>
      </v-container>
    </v-bottom-sheet>
    <v-snackbar
      v-model="jumpToPreviousBook"
      :timeout="jumpConfirmationDelay"
      vertical
      top
      color="rgba(0, 0, 0, 0.7)"
      multi-line
      class="mt-12"
    >
      <div class="title pa-6">
        <p>You're at the beginning<br/>of the book.</p>
        <p v-if="!$_.isEmpty(siblingPrevious)">Click or press previous again<br/>to move to the previous book.</p>
      </div>
    </v-snackbar>

    <v-snackbar
      v-model="jumpToNextBook"
      :timeout="jumpConfirmationDelay"
      vertical
      top
      color="rgba(0, 0, 0, 0.7)"
      multi-line
      class="mt-12"
    >
      <div class="title pa-6">
        <p>You've reached the end<br/>of the book.</p>
        <p v-if="!$_.isEmpty(siblingNext)">Click or press next again<br/>to move to the next book.</p>
        <p v-else>Click or press next again<br/>to exit the reader.</p>
      </div>
    </v-snackbar>

    <v-snackbar v-model="snackReadingDirection"
                color="info"
                multi-line
                vertical
                top
    >
      <div>This book has specific reading direction set.</div>
      <div>Reading direction has been set to <span class="font-weight-bold">{{ readingDirs.find(x => x.value === readingDirection).text }}</span>.
      </div>
      <div>This only applies to this book and will not overwrite your settings.</div>
      <v-btn
        text
        @click="snackReadingDirection = false"
      >
        Dismiss
      </v-btn>
    </v-snackbar>
  </v-container>
</template>

<script lang="ts">
import SettingsSwitch from '@/components/SettingsSwitch.vue'
import SettingsSelect from '@/components/SettingsSelect.vue'
import ThumbnailExplorerDialog from '@/components/ThumbnailExplorerDialog.vue'

import { checkWebpFeature } from '@/functions/check-webp'
import { bookPageUrl } from '@/functions/urls'
import Vue from 'vue'
import { getBookTitleCompact } from '@/functions/book-title'
import { ReadingDirection } from '@/types/enum-books'

const cookieFit = 'webreader.fit'
const cookieReadingDirection = 'webreader.readingDirection'
const cookieDoublePages = 'webreader.doublePages'
const cookieSwipe = 'webreader.swipe'
const cookieAnimations = 'webreader.animations'
const cookieBackground = 'webreader.background'

enum ImageFit {
  WIDTH = 'width',
  HEIGHT = 'height',
  ORIGINAL = 'original'
}

export default Vue.extend({
  name: 'BookReader',
  components: { SettingsSwitch, SettingsSelect, ThumbnailExplorerDialog },
  data: () => {
    return {
      ImageFit,
      book: {} as BookDto,
      series: {} as SeriesDto,
      siblingPrevious: {} as BookDto,
      siblingNext: {} as BookDto,
      jumpToNextBook: false,
      jumpToPreviousBook: false,
      jumpConfirmationDelay: 3000,
      snackReadingDirection: false,
      pages: [] as PageDto[],
      supportedMediaTypes: ['image/jpeg', 'image/png', 'image/gif'],
      convertTo: 'jpeg',
      carouselPage: 0,
      showThumbnailsExplorer: false,
      toolbar: false,
      menu: false,
      dialogGoto: false,
      goToPage: 1,
      settings: {
        doublePages: false,
        swipe: true,
        imageFits: Object.values(ImageFit),
        fit: ImageFit.HEIGHT,
        readingDirection: ReadingDirection.LEFT_TO_RIGHT,
        animations: true,
        backgroundColor: 'black',
      },
      readingDirs: [
        { text: 'Left to right', value: ReadingDirection.LEFT_TO_RIGHT },
        { text: 'Right to left', value: ReadingDirection.RIGHT_TO_LEFT },
        { text: 'Vertical', value: ReadingDirection.VERTICAL },
      ],
      imageFits: [
        { text: 'Fit to height', value: ImageFit.HEIGHT },
        { text: 'Fit to width', value: ImageFit.WIDTH },
        { text: 'Original', value: ImageFit.ORIGINAL },
      ],
      backgroundColors: [
        { text: 'White', value: 'white' },
        { text: 'Black', value: 'black' },
      ],
    }
  },
  created () {
    checkWebpFeature('lossy', (feature, isSupported) => {
      if (isSupported) {
        this.supportedMediaTypes.push('image/webp')
      }
    })
  },
  async mounted () {
    window.addEventListener('keydown', this.keyPressed)

    this.loadFromCookie(cookieReadingDirection, (v) => {
      this.readingDirection = v
    })
    this.loadFromCookie(cookieAnimations, (v) => {
      this.animations = (v === 'true')
    })
    this.loadFromCookie(cookieDoublePages, (v) => {
      this.doublePages = (v === 'true')
    })
    this.loadFromCookie(cookieSwipe, (v) => {
      this.swipe = (v === 'true')
    })
    this.loadFromCookie(cookieFit, (v) => {
      if (v) {
        this.imageFit = v
      }
    })
    this.loadFromCookie(cookieBackground, (v) => {
      if (v) {
        this.backgroundColor = v
      }
    })

    this.setup(this.bookId, Number(this.$route.query.page))
  },
  destroyed () {
    window.removeEventListener('keydown', this.keyPressed)
  },
  props: {
    bookId: {
      type: Number,
      required: true,
    },
  },
  async beforeRouteUpdate (to, from, next) {
    if (to.params.bookId !== from.params.bookId) {
      // route update means going to previous/next book, in this case we start from first page
      this.setup(Number(to.params.bookId), 1)
    }
    next()
  },
  watch: {
    currentPage (val) {
      this.updateRoute()
      this.goToPage = val
    },
    async book (val) {
      if (this.$_.has(val, 'name')) {
        this.series = await this.$komgaSeries.getOneSeries(val.seriesId)
        document.title = `Komga - ${getBookTitleCompact(val.metadata.title, this.series.metadata.title)}`
      }
    },
  },
  computed: {
    currentSlide (): number {
      return this.carouselPage + 1
    },
    currentPage (): number {
      return this.doublePages ? this.toSinglePages(this.currentSlide) : this.currentSlide
    },
    canPrev (): boolean {
      return this.currentSlide > 1
    },
    canNext (): boolean {
      return this.currentSlide < this.slidesCount
    },
    progress (): number {
      return this.currentPage / this.pagesCount * 100
    },
    maxHeight (): number | null {
      return this.imageFit === ImageFit.HEIGHT ? this.$vuetify.breakpoint.height : null
    },
    imgStyle (): string {
      return this.imageFit === ImageFit.WIDTH ? 'height:intrinsic' : ''
    },
    slidesRange (): number[] {
      if (!this.doublePages) {
        return this.$_.range(1, this.pagesCount + 1)
      }
      // for double pages the first and last pages are shown as single, while others are doubled
      const ret = this.$_.range(2, this.pagesCount, 2)
      ret.unshift(1)
      ret.push(this.pagesCount)
      return ret
    },
    slidesCount (): number {
      return this.slidesRange.length
    },
    pagesCount (): number {
      return this.pages.length
    },
    bookTitle (): string {
      return getBookTitleCompact(this.book.metadata.title, this.series.metadata.title)
    },

    animations: {
      get: function (): boolean {
        return this.settings.animations
      },
      set: function (animations: boolean): void {
        this.settings.animations = animations
        this.$cookies.set(cookieAnimations, animations, Infinity)
      },
    },
    readingDirection: {
      get: function (): ReadingDirection {
        return this.settings.readingDirection
      },
      set: function (readingDirection: ReadingDirection): void {
        this.settings.readingDirection = readingDirection
        this.$cookies.set(cookieReadingDirection, readingDirection, Infinity)
      },
    },
    flipDirection (): boolean {
      return this.readingDirection === ReadingDirection.RIGHT_TO_LEFT
    },
    vertical (): boolean {
      return this.readingDirection === ReadingDirection.VERTICAL
    },
    imageFit: {
      get: function (): ImageFit {
        return this.settings.fit
      },
      set: function (fit: ImageFit): void {
        this.settings.fit = fit
        this.$cookies.set(cookieFit, fit, Infinity)
      },
    },
    backgroundColor: {
      get: function (): string {
        return this.settings.backgroundColor
      },
      set: function (color: string): void {
        this.settings.backgroundColor = color
        this.$cookies.set(cookieBackground, color, Infinity)
      },
    },
    doublePages: {
      get: function (): boolean {
        return this.settings.doublePages
      },
      set: function (doublePages: boolean): void {
        const current = this.currentPage
        this.settings.doublePages = doublePages
        this.goTo(current)
        this.$cookies.set(cookieDoublePages, doublePages, Infinity)
      },
    },
    swipe: {
      get: function (): boolean {
        return this.settings.swipe
      },
      set: function (swipe: boolean): void {
        this.settings.swipe = swipe
        this.$cookies.set(cookieSwipe, swipe, Infinity)
      },
    },
  },
  methods: {
    keyPressed (e: KeyboardEvent) {
      switch (e.key) {
        case 'PageUp':
        case 'ArrowRight':
          this.flipDirection ? this.prev() : this.next()
          break
        case 'PageDown':
        case 'ArrowLeft':
          this.flipDirection ? this.next() : this.prev()
          break
        case 'ArrowDown':
          if (this.vertical) this.next()
          break
        case 'ArrowUp':
          if (this.vertical) this.prev()
          break
        case 'Home':
          this.goToFirst()
          break
        case 'End':
          this.goToLast()
          break
        case 'm':
          this.toolbar = !this.toolbar
          break
        case 's':
          this.menu = !this.menu
          break
        case 't':
          this.showThumbnailsExplorer = !this.showThumbnailsExplorer
          break
        case 'Escape':
          if (this.showThumbnailsExplorer) {
            this.showThumbnailsExplorer = false
            break
          }
          if (this.menu) {
            this.menu = false
            break
          }
          if (this.toolbar) {
            this.toolbar = false
            break
          }
          this.closeBook()
          break
      }
    },
    async setup (bookId: number, page: number) {
      this.book = await this.$komgaBooks.getBook(bookId)
      this.pages = await this.$komgaBooks.getBookPages(bookId)
      if (page >= 1 && page <= this.pagesCount) {
        this.goTo(page)
      } else {
        this.goToFirst()
      }

      // set non-persistent reading direction if exists in metadata
      switch (this.book.metadata.readingDirection) {
        case ReadingDirection.LEFT_TO_RIGHT:
        case ReadingDirection.RIGHT_TO_LEFT:
        case ReadingDirection.VERTICAL:
          if (this.readingDirection !== this.book.metadata.readingDirection) {
            // bypass setter so cookies aren't set
            this.settings.readingDirection = this.book.metadata.readingDirection
            this.snackReadingDirection = true
          }
          break
      }

      try {
        this.siblingNext = await this.$komgaBooks.getBookSiblingNext(bookId)
      } catch (e) {
        this.siblingNext = {} as BookDto
      }
      try {
        this.siblingPrevious = await this.$komgaBooks.getBookSiblingPrevious(bookId)
      } catch (e) {
        this.siblingPrevious = {} as BookDto
      }
    },
    getPageUrl (page: number): string {
      if (!this.supportedMediaTypes.includes(this.pages[page - 1].mediaType)) {
        return bookPageUrl(this.bookId, page, this.convertTo)
      } else {
        return bookPageUrl(this.bookId, page)
      }
    },
    turnRight () {
      if (this.vertical) return
      return this.flipDirection ? this.prev() : this.next()
    },
    turnLeft () {
      if (this.vertical) return
      return this.flipDirection ? this.next() : this.prev()
    },
    verticalPrev () {
      if (this.vertical) this.prev()
    },
    verticalNext () {
      if (this.vertical) this.next()
    },
    prev () {
      if (this.canPrev) {
        this.carouselPage--
        window.scrollTo(0, 0)
      } else {
        if (this.jumpToPreviousBook) {
          if (!this.$_.isEmpty(this.siblingPrevious)) {
            this.jumpToPreviousBook = false
            this.$router.push({ name: 'read-book', params: { bookId: this.siblingPrevious.id.toString() } })
          }
        } else {
          this.jumpToPreviousBook = true
        }
      }
    },
    next () {
      if (this.canNext) {
        this.carouselPage++
        window.scrollTo(0, 0)
      } else {
        if (this.jumpToNextBook) {
          if (this.$_.isEmpty(this.siblingNext)) {
            this.closeBook()
          } else {
            this.jumpToNextBook = false
            this.$router.push({ name: 'read-book', params: { bookId: this.siblingNext.id.toString() } })
          }
        } else {
          this.jumpToNextBook = true
        }
      }
    },
    goTo (page: number) {
      this.carouselPage = this.doublePages ? this.toDoublePages(page) - 1 : page - 1
    },
    goToFirst () {
      this.goTo(1)
    },
    goToLast () {
      this.goTo(this.pagesCount)
    },
    updateRoute () {
      this.$router.replace({
        name: this.$route.name,
        params: { bookId: this.$route.params.bookId },
        query: {
          page: this.currentPage.toString(),
        },
      })
    },
    closeBook () {
      this.$router.push({ name: 'browse-book', params: { bookId: this.bookId.toString() } })
    },
    toSinglePages (i: number): number {
      if (i === 1) return 1
      if (i === this.slidesCount) return this.pagesCount
      return (i - 1) * 2
    },
    toDoublePages (i: number): number {
      let ret = Math.floor(i / 2) + 1
      if (i === this.pagesCount && this.pagesCount % 2 === 1) {
        ret++
      }
      return ret
    },
    eagerLoad (p: number): boolean {
      return Math.abs(this.currentPage - p) <= 2
    },
    maxWidth (p: number): number | null {
      if (this.imageFit !== ImageFit.WIDTH) {
        return null
      }
      if (this.doublePages && p !== 1 && p !== this.pagesCount) {
        return this.$vuetify.breakpoint.width / 2
      }
      return this.$vuetify.breakpoint.width
    },
    loadFromCookie (cookieKey: string, setter: (value: any) => void): void {
      if (this.$cookies.isKey(cookieKey)) {
        let value = this.$cookies.get(cookieKey)
        setter(value)
      }
    },
  },
})
</script>

<style scoped>
.settings {
  /*position: absolute;*/
  z-index: 2;
}

.top {
  top: 0;
}

.full-height {
  height: 100%;
}

.full-width {
  width: 100%;
}

.left-quarter {
  left: 0;
  width: 20%;
  position: absolute;
}

.right-quarter {
  right: 0;
  width: 20%;
  position: absolute;
}

.center-half {
  left: 20%;
  width: 60%;
  position: absolute;
}
</style>
