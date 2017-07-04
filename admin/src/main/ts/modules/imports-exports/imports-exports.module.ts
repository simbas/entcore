import { InfraComponentsModule } from 'infra-components'
import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms'
import { RouterModule } from '@angular/router'
import { NgModule } from '@angular/core'
import { SijilModule } from 'sijil'
import { UxModule } from '..'
import { routes } from './routing/routes'
import {
    ImportsExportsRoot,
    ImportCSV,
    MappingsTable
 } from './components'

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        UxModule,
        SijilModule.forChild(),
        InfraComponentsModule.forChild(),
        RouterModule.forChild(routes)
    ],
    declarations: [
        ImportsExportsRoot,
        ImportCSV,
        MappingsTable
    ],
    providers: [
        // TODO Service to communicate with server API
    ],
    exports: [
        RouterModule
    ]
})
export class ImportsExportsModule {}
