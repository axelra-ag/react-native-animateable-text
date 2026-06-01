declare module 'react-native/Libraries/NativeComponent/ViewConfig' {
    export type PartialViewConfig = {
        validAttributes?: Record<string, unknown>;
        directEventTypes?: Record<
            string,
            {
                registrationName?: string;
            }
        >;
        bubblingEventTypes?: Record<
            string,
            {
                phasedRegistrationNames?: {
                    captured?: string;
                    bubbled?: string;
                    skipBubbling?: string;
                };
            }
        >;
        uiViewClassName?: string;
    };

    export function createViewConfig<T extends PartialViewConfig>(
        partialViewConfig: T,
    ): T;
}
