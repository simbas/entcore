import { SijilModule } from 'sijil'
import { InfraComponentsModule } from 'infra-components'
import { FormsModule } from '@angular/forms'
import { CommonModule } from '@angular/common'
import { NgModule } from '@angular/core'


import { FormErrors, FormField, ListComponent, PanelSection, SideLayout, SpinnerComponent, Datepicker, SimpleSelect } from './components'
import { AnchorDirective, DynamicComponent} from './directives'
import { MapToArrayPipe } from './pipes'

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        SijilModule.forChild(),
        InfraComponentsModule
    ],
    declarations: [
        SpinnerComponent,
        SideLayout,
        PanelSection,
        ListComponent,
        FormField,
        FormErrors,
        AnchorDirective,
        Datepicker,
        MapToArrayPipe,
        DynamicComponent,
        SimpleSelect
    ],
    exports: [
        SpinnerComponent,
        SideLayout,
        PanelSection,
        ListComponent,
        FormField,
        FormErrors,
        AnchorDirective,
        Datepicker,
        MapToArrayPipe,
        DynamicComponent,
        SimpleSelect
    ],
    entryComponents: [SimpleSelect]
})
export class UxModule{}