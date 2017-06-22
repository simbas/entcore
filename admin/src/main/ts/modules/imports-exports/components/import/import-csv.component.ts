import { Component, OnDestroy, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, Input, Output
    , OnChanges, EventEmitter, ElementRef } from '@angular/core'
import { ActivatedRoute, Data, Router } from '@angular/router'
import { BundlesService } from 'sijil'


@Component({
    selector: 'import-csv',
    template: `
         <wizard>
            <step #step1 name="{{ 'import.files.deposit' | translate }}" [isActived]="true" [class.active]="step1.isActived">
                <h2 class="panel-header">{{ 'import.files.deposit' | translate }}</h2>
                <form #step1Form="ngForm" (ngSubmit)="createNewUser()">
                    <h3>{{ 'import.files.deposit' | translate }}</h3>
                    <form-field label="Teacher">
                        <input type="checkbox"  name="teacher">
                    </form-field>
                    <form-field label="Student">
                        <input type="checkbox"  name="student">
                    </form-field>
                    <form-field label="Relative">
                        <input type="checkbox"  name="relative">
                    </form-field>
                    <form-field label="Personnel">
                        <input type="checkbox"  name="personnel">
                    </form-field>
                    <form-field label="Guest">
                        <input type="checkbox"  name="Guest">
                    </form-field>

                    <h3>{{ 'import.parameters' | translate }}</h3>
                    <form-field label="import.step1.deleteAccountOption">
                        <input type="checkbox"  name="deleteAccountOption">
                    </form-field>
                    <form-field label="import.step1.transitionOption">
                        <input type="checkbox"  name="transitionOption">
                    </form-field>
                </form>
            </step>
            <step #step2 name="{{ 'import.fields.checking' | translate }}" [class.active]="step2.isActived">
                <h2 class="panel-header">{{ 'import.fields.checking' | translate }}</h2>

            </step>
            <step #step3 name="{{ 'import.class.checking' | translate }}" [class.active]="step3.isActived">
                <h2 class="panel-header">{{ 'import.class.checking' | translate }}</h2>
            </step>
            <step #step4 name="{{ 'import.report' | translate }}" [class.active]="step4.isActived">
                <h2 class="panel-header">{{ 'import.report' | translate }}</h2>
            </step>
        </wizard>
    `
})

export class ImportCSV implements OnInit, OnDestroy {

    constructor(private _eref: ElementRef,
        private bundles: BundlesService,
        private cdRef: ChangeDetectorRef){}

    translate = (...args) => { return (<any> this.bundles.translate)(...args) }

    ngOnInit(): void {
    }

    ngOnDestroy(): void {
    }
}
